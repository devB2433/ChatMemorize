"""Shared test fixtures for WeChatMem backend tests."""
import pytest
import pytest_asyncio
from httpx import AsyncClient, ASGITransport
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession

from app.database import Base, get_db
from app.main import app

# In-memory SQLite for all tests
_test_engine = create_async_engine("sqlite+aiosqlite://", echo=False)
_test_session = async_sessionmaker(_test_engine, class_=AsyncSession, expire_on_commit=False)


async def _override_get_db():
    async with _test_session() as session:
        yield session


app.dependency_overrides[get_db] = _override_get_db

# Disable rate limiting in tests
from app.limiter import limiter
limiter.enabled = False


@pytest_asyncio.fixture(autouse=True)
async def setup_db():
    """Create fresh tables before each test."""
    from app.models.user import User  # noqa: F401
    from app.models.conversation import Conversation  # noqa: F401
    from app.models.message import Message  # noqa: F401
    async with _test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)
        await conn.run_sync(Base.metadata.create_all)
    yield
    app.dependency_overrides[get_db] = _override_get_db


@pytest_asyncio.fixture
async def client():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        yield c


@pytest_asyncio.fixture
async def auth_headers(client: AsyncClient) -> dict[str, str]:
    """Register a test user and return auth headers."""
    resp = await client.post(
        "/api/v1/auth/register",
        json={"username": "testuser", "password": "testpass123"},
    )
    token = resp.json()["token"]
    return {"Authorization": f"Bearer {token}"}


SAMPLE_CHAT = (
    "张三 2024-01-01 10:00\n你好\n\n"
    "李四 2024-01-01 10:01\n你好呀\n\n"
    "张三 2024-01-01 10:02\n今天天气不错"
)
