"""Summary generation service."""
import json
import sqlalchemy as sa
from pathlib import Path
from app.database import async_session
from app.config import settings
from app.models.conversation import Conversation
from app.models.message import Message
from app.services.llm import generate_text
from datetime import datetime, timezone

SUMMARIES_DIR = settings.data_dir / "summaries"
SUMMARIES_DIR.mkdir(parents=True, exist_ok=True)


def _save_summary_md(conv: Conversation, msgs, summary: str) -> Path:
    """Save summary as a Markdown file and return the file path."""
    participants = json.loads(conv.participants)
    created = conv.created_at[:10] if conv.created_at else "unknown"

    lines = [
        f"# {conv.title or '未命名对话'}",
        "",
        f"- **对话ID**: `{conv.id}`",
        f"- **参与者**: {', '.join(participants)}",
        f"- **消息数**: {conv.message_count}",
        f"- **创建时间**: {conv.created_at}",
        f"- **生成时间**: {datetime.now(timezone.utc).isoformat()}",
        "",
        "## 摘要",
        "",
        summary,
        "",
        "## 对话记录",
        "",
    ]
    for m in msgs:
        ts = f" ({m.timestamp})" if m.timestamp else ""
        lines.append(f"**{m.sender}**{ts}: {m.content}  ")

    md_path = SUMMARIES_DIR / f"{conv.id}.md"
    md_path.write_text("\n".join(lines), encoding="utf-8")
    return md_path


def delete_summary_md(conversation_id: str) -> None:
    """Delete the summary Markdown file for a conversation."""
    md_path = SUMMARIES_DIR / f"{conversation_id}.md"
    if md_path.is_file():
        md_path.unlink()


async def generate_summary(conversation_id: str) -> str:
    async with async_session() as db:
        msgs = (
            await db.execute(
                sa.select(Message)
                .where(Message.conversation_id == conversation_id)
                .order_by(Message.sequence)
            )
        ).scalars().all()

        if not msgs:
            return ""

        chat_text = "\n".join(f"[{m.sender}]: {m.content}" for m in msgs)
        # Truncate if too long
        if len(chat_text) > 3000:
            chat_text = chat_text[:3000] + "\n..."

        system = "你是一个聊天记录摘要助手。请用简洁的中文总结以下对话的主要内容和关键信息。"
        summary = await generate_text(chat_text, system)

        conv = (
            await db.execute(
                sa.select(Conversation).where(Conversation.id == conversation_id)
            )
        ).scalar_one_or_none()
        if conv:
            conv.summary = summary
            conv.updated_at = datetime.now(timezone.utc).isoformat()
            await db.commit()
            # Persist as Markdown file
            _save_summary_md(conv, msgs, summary)

        return summary
