import sqlalchemy as sa
from app.database import Base


class Message(Base):
    __tablename__ = "messages"

    id = sa.Column(sa.Text, primary_key=True)
    conversation_id = sa.Column(
        sa.Text, sa.ForeignKey("conversations.id", ondelete="CASCADE"), nullable=False
    )
    sender = sa.Column(sa.Text, nullable=False)
    content = sa.Column(sa.Text, nullable=False)
    timestamp = sa.Column(sa.Text)
    sequence = sa.Column(sa.Integer, nullable=False)
    msg_type = sa.Column(sa.Text, default="text")  # text, image, media
    image_path = sa.Column(sa.Text)  # relative path to stored image file
    created_at = sa.Column(sa.Text, nullable=False)
