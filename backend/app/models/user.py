import sqlalchemy as sa
from app.database import Base


class User(Base):
    __tablename__ = "users"

    id = sa.Column(sa.Text, primary_key=True)
    username = sa.Column(sa.Text, unique=True, nullable=False)
    password = sa.Column(sa.Text, nullable=False)  # bcrypt hash
    created_at = sa.Column(sa.Text, nullable=False)
