from pydantic import BaseModel
from app.schemas.message import MessageOut


class ConversationCreate(BaseModel):
    text: str  # raw WeChat shared text
    title: str | None = None


class ConversationUpdate(BaseModel):
    title: str | None = None


class ConversationBrief(BaseModel):
    id: str
    title: str | None
    participants: list[str]
    message_count: int
    summary: str | None
    created_at: str
    updated_at: str


class ConversationDetail(ConversationBrief):
    messages: list[MessageOut]


class PaginatedConversations(BaseModel):
    items: list[ConversationBrief]
    total: int
    page: int
    page_size: int
