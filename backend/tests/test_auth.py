"""Test JWT auth flow: register, login, CRUD with token, 401 without token."""
import pytest
import pytest_asyncio
from httpx import AsyncClient, ASGITransport
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession

from app.database import Base, get_db
from app.main import app

# Use in-memory SQLite for tests
_test_engine = create_async_engine("sqlite+aiosqlite://", echo=False)
_test_session = async_sessionmaker(_test_engine, class_=AsyncSession, expire_on_commit=False)


async def _override_get_db():
    async with _test_session() as session:
        yield session


app.dependency_overrides[get_db] = _override_get_db


@pytest_asyncio.fixture(autouse=True)
async def setup_db():
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


@pytest.mark.asyncio
async def test_register(client: AsyncClient):
    resp = await client.post("/api/v1/auth/register", json={"username": "alice", "password": "123456"})
    assert resp.status_code == 201
    data = resp.json()
    assert "token" in data
    assert data["token_type"] == "bearer"


@pytest.mark.asyncio
async def test_register_duplicate(client: AsyncClient):
    await client.post("/api/v1/auth/register", json={"username": "bob", "password": "123456"})
    resp = await client.post("/api/v1/auth/register", json={"username": "bob", "password": "654321"})
    assert resp.status_code == 409


@pytest.mark.asyncio
async def test_login(client: AsyncClient):
    await client.post("/api/v1/auth/register", json={"username": "carol", "password": "abcdef"})
    resp = await client.post("/api/v1/auth/login", json={"username": "carol", "password": "abcdef"})
    assert resp.status_code == 200
    assert "token" in resp.json()


@pytest.mark.asyncio
async def test_login_wrong_password(client: AsyncClient):
    await client.post("/api/v1/auth/register", json={"username": "dave", "password": "abcdef"})
    resp = await client.post("/api/v1/auth/login", json={"username": "dave", "password": "wrong"})
    assert resp.status_code == 401


@pytest.mark.asyncio
async def test_me(client: AsyncClient):
    reg = await client.post("/api/v1/auth/register", json={"username": "eve", "password": "123456"})
    token = reg.json()["token"]
    resp = await client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 200
    assert resp.json()["username"] == "eve"


@pytest.mark.asyncio
async def test_conversations_require_auth(client: AsyncClient):
    resp = await client.get("/api/v1/conversations")
    assert resp.status_code in (401, 403)  # HTTPBearer returns 403 or 401 depending on version


@pytest.mark.asyncio
async def test_create_and_list_conversations(client: AsyncClient):
    # Register and get token
    reg = await client.post("/api/v1/auth/register", json={"username": "frank", "password": "123456"})
    token = reg.json()["token"]
    headers = {"Authorization": f"Bearer {token}"}

    # Create conversation
    text = "2024-01-01 10:00:00\n张三\n你好\n\n2024-01-01 10:01:00\n李四\n你好呀"
    resp = await client.post("/api/v1/conversations", json={"text": text}, headers=headers)
    assert resp.status_code == 201
    conv_id = resp.json()["id"]

    # List - should see the conversation
    resp = await client.get("/api/v1/conversations", headers=headers)
    assert resp.status_code == 200
    assert resp.json()["total"] == 1

    # Another user should not see it
    reg2 = await client.post("/api/v1/auth/register", json={"username": "grace", "password": "123456"})
    token2 = reg2.json()["token"]
    resp = await client.get("/api/v1/conversations", headers={"Authorization": f"Bearer {token2}"})
    assert resp.status_code == 200
    assert resp.json()["total"] == 0

    # Other user cannot access the conversation detail
    resp = await client.get(f"/api/v1/conversations/{conv_id}", headers={"Authorization": f"Bearer {token2}"})
    assert resp.status_code == 404
