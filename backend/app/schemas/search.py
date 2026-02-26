from pydantic import BaseModel


class SearchRequest(BaseModel):
    query: str
    top_k: int = 10
    conversation_id: str | None = None


class SearchResult(BaseModel):
    message_id: str
    conversation_id: str
    sender: str
    content: str
    timestamp: str | None
    score: float
    type: str = "message"  # "message" or "summary"
    title: str | None = None  # conversation title, present for summary hits


class SearchResponse(BaseModel):
    results: list[SearchResult]
    query: str


class AskRequest(BaseModel):
    question: str
    conversation_id: str | None = None
    top_k: int = 5


class AskResponse(BaseModel):
    answer: str
    sources: list[SearchResult]
