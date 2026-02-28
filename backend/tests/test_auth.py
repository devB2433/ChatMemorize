"""Test JWT auth flow: register, login, refresh, me, 401 without token."""
import pytest
from httpx import AsyncClient


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
    assert resp.status_code in (401, 403)


@pytest.mark.asyncio
async def test_refresh_token(client: AsyncClient):
    reg = await client.post("/api/v1/auth/register", json={"username": "frank", "password": "123456"})
    token = reg.json()["token"]
    headers = {"Authorization": f"Bearer {token}"}
    resp = await client.post("/api/v1/auth/refresh", headers=headers)
    assert resp.status_code == 200
    new_token = resp.json()["token"]
    assert "token" in resp.json()
    # New token should work
    resp2 = await client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {new_token}"})
    assert resp2.status_code == 200
