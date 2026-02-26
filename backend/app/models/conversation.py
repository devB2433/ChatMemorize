import sqlalchemy as sa
from app.database import Base


class Conversation(Base):
    __tablename__ = "conversations"

    id = sa.Column(sa.Text, primary_key=True)
    user_id = sa.Column(sa.Text, sa.ForeignKey("users.id"), nullable=False)
    title = sa.Column(sa.Text)
    participants = sa.Column(sa.Text, nullable=False)  # JSON array
    message_count = sa.Column(sa.Integer, default=0)
    summary = sa.Column(sa.Text)
    created_at = sa.Column(sa.Text, nullable=False)
    updated_at = sa.Column(sa.Text, nullable=False)
