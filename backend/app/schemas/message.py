from pydantic import BaseModel


class MessageOut(BaseModel):
    id: str
    sender: str
    content: str
    timestamp: str | None
    sequence: int
    msg_type: str = "text"
    image_url: str | None = None
