import asyncio
import json
import logging
import uuid
import shutil
from datetime import datetime, timezone
from pathlib import Path

import sqlalchemy as sa
from fastapi import APIRouter, Depends, HTTPException, BackgroundTasks, UploadFile, File, Form, Request
from fastapi.responses import FileResponse
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models.conversation import Conversation
from app.models.message import Message
from app.models.user import User
from app.services.auth import get_current_user
from app.schemas.conversation import (
    ConversationCreate,
    ConversationUpdate,
    ConversationBrief,
    ConversationDetail,
    PaginatedConversations,
)
from app.schemas.message import MessageOut
from app.services.parser import parse_wechat_text
from app.config import settings
from app.limiter import limiter

router = APIRouter(prefix="/conversations", tags=["conversations"])

IMAGES_DIR = settings.data_dir / "images"
IMAGES_DIR.mkdir(parents=True, exist_ok=True)


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _image_url(image_path: str | None) -> str | None:
    if not image_path:
        return None
    return f"/api/v1/conversations/images/{image_path}"


async def _save_images(conv_id: str, files: list[UploadFile]) -> list[str]:
    """Save uploaded images, return list of relative paths."""
    conv_dir = IMAGES_DIR / conv_id
    conv_dir.mkdir(parents=True, exist_ok=True)
    paths = []
    for i, f in enumerate(files):
        ext = Path(f.filename or "img.jpg").suffix or ".jpg"
        filename = f"{i:04d}{ext}"
        dest = conv_dir / filename
        content = await f.read()
        dest.write_bytes(content)
        paths.append(f"{conv_id}/{filename}")
    return paths


# --- Upload: JSON body (text only, backward compatible) ---
@router.post("", response_model=ConversationBrief, status_code=201)
@limiter.limit(settings.rate_limit_api)
async def create_conversation(
    request: Request,
    body: ConversationCreate,
    background_tasks: BackgroundTasks,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    return await _create_conv(body.text, body.title, [], background_tasks, db, current_user)


# --- Upload: Multipart (text + images from Android) ---
@router.post("/upload", response_model=ConversationBrief, status_code=201)
@limiter.limit(settings.rate_limit_api)
async def upload_conversation(
    request: Request,
    background_tasks: BackgroundTasks,
    text: str = Form(...),
    title: str | None = Form(None),
    images: list[UploadFile] = File(default=[]),
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    if images:
        await _validate_upload_size(images)
    return await _create_conv(text, title, images, background_tasks, db, current_user)


async def _validate_upload_size(images: list[UploadFile]) -> None:
    """Check individual image size and total upload size."""
    max_img = settings.max_image_size_mb * 1024 * 1024
    max_total = settings.max_upload_total_mb * 1024 * 1024
    total = 0
    for f in images:
        data = await f.read()
        total += len(data)
        if len(data) > max_img:
            raise HTTPException(413, f"单张图片不能超过 {settings.max_image_size_mb}MB")
        await f.seek(0)  # reset for later read
    if total > max_total:
        raise HTTPException(413, f"上传总大小不能超过 {settings.max_upload_total_mb}MB")


async def _create_conv(
    text: str,
    title: str | None,
    images: list[UploadFile],
    background_tasks: BackgroundTasks,
    db: AsyncSession,
    current_user: User,
) -> ConversationBrief:
    parsed = parse_wechat_text(text)
    if not parsed.messages:
        raise HTTPException(400, "No messages parsed from text")

    conv_id = str(uuid.uuid4())
    now = _now()
    title = title or f"对话 - {', '.join(parsed.participants[:3])}"

    # Save images to disk
    image_paths = await _save_images(conv_id, images) if images else []

    conv = Conversation(
        id=conv_id,
        user_id=current_user.id,
        title=title,
        participants=json.dumps(parsed.participants, ensure_ascii=False),
        message_count=len(parsed.messages),
        created_at=now,
        updated_at=now,
    )
    db.add(conv)

    msg_rows = []
    for pm in parsed.messages:
        # Map image placeholder to actual file path
        img_path = None
        if pm.msg_type == "image" and pm.image_index is not None:
            if pm.image_index < len(image_paths):
                img_path = image_paths[pm.image_index]

        msg_rows.append(
            Message(
                id=str(uuid.uuid4()),
                conversation_id=conv_id,
                sender=pm.sender,
                content=pm.content,
                timestamp=pm.timestamp,
                sequence=pm.sequence,
                msg_type=pm.msg_type,
                image_path=img_path,
                created_at=now,
            )
        )
    db.add_all(msg_rows)
    await db.commit()

    background_tasks.add_task(_post_upload, conv_id, msg_rows)

    return ConversationBrief(
        id=conv_id,
        title=title,
        participants=parsed.participants,
        message_count=len(parsed.messages),
        summary=None,
        created_at=now,
        updated_at=now,
    )


# --- Serve uploaded images ---
@router.get("/images/{conv_id}/{filename}")
async def get_image(conv_id: str, filename: str):
    path = IMAGES_DIR / conv_id / filename
    if not path.is_file():
        raise HTTPException(404, "Image not found")
    return FileResponse(path)


async def _post_upload(conv_id: str, msg_rows: list[Message]):
    """Background task: embed messages and generate summary."""
    log = logging.getLogger(__name__)
    try:
        from app.services.vectorstore import add_messages
        await asyncio.to_thread(add_messages, conv_id, msg_rows)
        log.info("Vectorized %d messages for %s", len(msg_rows), conv_id)
    except Exception:
        log.exception("Vectorization failed for %s", conv_id)
    try:
        from app.services.summary import generate_summary
        summary = await generate_summary(conv_id)
        log.info("Summary generated for %s", conv_id)
        if summary:
            from app.services.vectorstore import add_summary
            # Fetch conversation metadata for richer vector context
            from app.database import async_session
            async with async_session() as db:
                conv = (await db.execute(
                    sa.select(Conversation).where(Conversation.id == conv_id)
                )).scalar_one_or_none()
            title = conv.title or "" if conv else ""
            participants = conv.participants or "" if conv else ""
            await asyncio.to_thread(add_summary, conv_id, summary, title, participants)
            log.info("Summary vectorized for %s", conv_id)
    except Exception:
        log.exception("Summary generation failed for %s", conv_id)


def _conv_to_brief(c: Conversation) -> ConversationBrief:
    return ConversationBrief(
        id=c.id,
        title=c.title,
        participants=json.loads(c.participants),
        message_count=c.message_count,
        summary=c.summary,
        created_at=c.created_at,
        updated_at=c.updated_at,
    )


def _msg_to_out(m: Message) -> MessageOut:
    return MessageOut(
        id=m.id,
        sender=m.sender,
        content=m.content,
        timestamp=m.timestamp,
        sequence=m.sequence,
        msg_type=m.msg_type or "text",
        image_url=_image_url(m.image_path),
    )


@router.get("", response_model=PaginatedConversations)
@limiter.limit(settings.rate_limit_api)
async def list_conversations(
    request: Request,
    page: int = 1,
    page_size: int = 20,
    q: str | None = None,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    page_size = min(page_size, settings.max_page_size)
    offset = (page - 1) * page_size

    user_filt = Conversation.user_id == current_user.id
    base = sa.select(Conversation).where(user_filt)
    count_q = sa.select(sa.func.count()).select_from(Conversation).where(user_filt)
    if q:
        filt = Conversation.title.contains(q) | Conversation.participants.contains(q)
        base = base.where(filt)
        count_q = count_q.where(filt)

    total = (await db.execute(count_q)).scalar() or 0
    rows = (
        await db.execute(
            base.order_by(Conversation.created_at.desc())
            .offset(offset)
            .limit(page_size)
        )
    ).scalars().all()

    return PaginatedConversations(
        items=[_conv_to_brief(c) for c in rows],
        total=total,
        page=page,
        page_size=page_size,
    )


@router.get("/{conv_id}", response_model=ConversationDetail)
@limiter.limit(settings.rate_limit_api)
async def get_conversation(
    request: Request,
    conv_id: str,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    conv = (
        await db.execute(
            sa.select(Conversation).where(
                Conversation.id == conv_id, Conversation.user_id == current_user.id
            )
        )
    ).scalar_one_or_none()
    if not conv:
        raise HTTPException(404, "Conversation not found")

    msgs = (
        await db.execute(
            sa.select(Message)
            .where(Message.conversation_id == conv_id)
            .order_by(Message.sequence)
        )
    ).scalars().all()

    return ConversationDetail(
        **_conv_to_brief(conv).model_dump(),
        messages=[_msg_to_out(m) for m in msgs],
    )


@router.patch("/{conv_id}", response_model=ConversationBrief)
@limiter.limit(settings.rate_limit_api)
async def update_conversation(
    request: Request,
    conv_id: str,
    body: ConversationUpdate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    conv = (
        await db.execute(
            sa.select(Conversation).where(
                Conversation.id == conv_id, Conversation.user_id == current_user.id
            )
        )
    ).scalar_one_or_none()
    if not conv:
        raise HTTPException(404, "Conversation not found")

    if body.title is not None:
        conv.title = body.title
    conv.updated_at = _now()
    await db.commit()
    return _conv_to_brief(conv)


@router.delete("/{conv_id}", status_code=204)
@limiter.limit(settings.rate_limit_api)
async def delete_conversation(
    request: Request,
    conv_id: str,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    conv = (
        await db.execute(
            sa.select(Conversation).where(
                Conversation.id == conv_id, Conversation.user_id == current_user.id
            )
        )
    ).scalar_one_or_none()
    if not conv:
        raise HTTPException(404, "Conversation not found")

    await db.execute(sa.delete(Message).where(Message.conversation_id == conv_id))
    await db.execute(sa.delete(Conversation).where(Conversation.id == conv_id))
    await db.commit()

    log = logging.getLogger(__name__)
    try:
        from app.services.vectorstore import delete_conversation_vectors
        delete_conversation_vectors(conv_id)
    except Exception:
        log.warning("Failed to delete vectors for %s", conv_id, exc_info=True)

    # Clean up summary Markdown file
    from app.services.summary import delete_summary_md
    delete_summary_md(conv_id)

    # Clean up image files
    img_dir = IMAGES_DIR / conv_id
    if img_dir.is_dir():
        shutil.rmtree(img_dir, ignore_errors=True)


@router.post("/{conv_id}/summary", response_model=ConversationBrief)
@limiter.limit(settings.rate_limit_api)
async def regenerate_summary(
    request: Request,
    conv_id: str,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    conv = (
        await db.execute(
            sa.select(Conversation).where(
                Conversation.id == conv_id, Conversation.user_id == current_user.id
            )
        )
    ).scalar_one_or_none()
    if not conv:
        raise HTTPException(404, "Conversation not found")

    from app.services.summary import generate_summary
    await generate_summary(conv_id)

    await db.refresh(conv)
    return _conv_to_brief(conv)
