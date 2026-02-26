import asyncio
from fastapi import APIRouter, HTTPException, Depends
from sqlalchemy.ext.asyncio import AsyncSession
import sqlalchemy as sa

from app.schemas.search import (
    SearchRequest,
    SearchResponse,
    SearchResult,
    AskRequest,
    AskResponse,
)
from app.database import get_db
from app.models.conversation import Conversation
from app.models.user import User
from app.services.auth import get_current_user

router = APIRouter(prefix="/search", tags=["search"])


def _to_search_result(item: dict) -> SearchResult:
    meta = item["metadata"]
    hit_type = meta.get("type", "message")
    return SearchResult(
        message_id=item["message_id"],
        conversation_id=meta["conversation_id"],
        sender=meta.get("sender", ""),
        content=item["content"],
        timestamp=meta.get("timestamp") or None,
        score=item["score"],
        type=hit_type,
        title=meta.get("title") or None,
    )


def _get_vector_search():
    try:
        from app.services.vectorstore import search as vector_search
        return vector_search
    except ImportError:
        raise HTTPException(503, "搜索服务未就绪，请安装 chromadb 和 sentence-transformers")


@router.post("", response_model=SearchResponse)
async def semantic_search(
    body: SearchRequest,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    vector_search = _get_vector_search()

    # Get user's conversation ids for filtering
    user_conv_ids = await _get_user_conv_ids(db, current_user.id, body.conversation_id)

    results = await asyncio.to_thread(
        vector_search, query=body.query, top_k=body.top_k,
        conversation_id=body.conversation_id,
    )
    # Filter results to only include user's conversations
    filtered = [r for r in results if r["metadata"]["conversation_id"] in user_conv_ids]
    return SearchResponse(
        results=[_to_search_result(r) for r in filtered],
        query=body.query,
    )


@router.post("/ask", response_model=AskResponse)
async def ask_question(
    body: AskRequest,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    vector_search = _get_vector_search()

    user_conv_ids = await _get_user_conv_ids(db, current_user.id, body.conversation_id)

    results = await asyncio.to_thread(
        vector_search, query=body.question, top_k=body.top_k,
        conversation_id=body.conversation_id,
    )
    filtered = [r for r in results if r["metadata"]["conversation_id"] in user_conv_ids]
    search_results = [_to_search_result(r) for r in filtered]

    from app.services.llm import ask_with_context

    # Split results into summaries and messages for structured context
    summary_parts = []
    message_parts = []
    for r in search_results:
        if r.type == "summary":
            label = f"[对话摘要 - {r.title or r.conversation_id}]"
            summary_parts.append(f"{label}: {r.content}")
        else:
            message_parts.append(f"[{r.sender}]: {r.content}")

    context_sections = []
    if summary_parts:
        context_sections.append("相关对话摘要:\n" + "\n".join(summary_parts))
    if message_parts:
        context_sections.append("相关聊天记录:\n" + "\n".join(message_parts))
    context = "\n\n".join(context_sections)

    answer = await ask_with_context(body.question, context)
    return AskResponse(answer=answer, sources=search_results)


async def _get_user_conv_ids(
    db: AsyncSession, user_id: str, conversation_id: str | None = None
) -> set[str]:
    """Return set of conversation IDs owned by user. If conversation_id given, verify ownership."""
    query = sa.select(Conversation.id).where(Conversation.user_id == user_id)
    if conversation_id:
        query = query.where(Conversation.id == conversation_id)
    rows = (await db.execute(query)).scalars().all()
    return set(rows)
