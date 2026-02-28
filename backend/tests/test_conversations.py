"""CRUD tests for conversations endpoints."""
import pytest
from httpx import AsyncClient

from tests.conftest import SAMPLE_CHAT


@pytest.mark.asyncio
async def test_create_conversation(client: AsyncClient, auth_headers: dict):
    resp = await client.post(
        "/api/v1/conversations",
        json={"text": SAMPLE_CHAT},
        headers=auth_headers,
    )
    assert resp.status_code == 201
    data = resp.json()
    assert data["message_count"] == 3
    assert "张三" in data["participants"]
    assert "李四" in data["participants"]


@pytest.mark.asyncio
async def test_create_with_title(client: AsyncClient, auth_headers: dict):
    resp = await client.post(
        "/api/v1/conversations",
        json={"text": SAMPLE_CHAT, "title": "测试会话"},
        headers=auth_headers,
    )
    assert resp.status_code == 201
    assert resp.json()["title"] == "测试会话"


@pytest.mark.asyncio
async def test_create_empty_text(client: AsyncClient, auth_headers: dict):
    resp = await client.post(
        "/api/v1/conversations",
        json={"text": ""},
        headers=auth_headers,
    )
    assert resp.status_code == 400


@pytest.mark.asyncio
async def test_list_conversations(client: AsyncClient, auth_headers: dict):
    # Create two conversations
    await client.post("/api/v1/conversations", json={"text": SAMPLE_CHAT}, headers=auth_headers)
    await client.post("/api/v1/conversations", json={"text": SAMPLE_CHAT, "title": "Second"}, headers=auth_headers)

    resp = await client.get("/api/v1/conversations", headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["total"] == 2
    assert len(data["items"]) == 2


@pytest.mark.asyncio
async def test_get_detail(client: AsyncClient, auth_headers: dict):
    create = await client.post(
        "/api/v1/conversations",
        json={"text": SAMPLE_CHAT},
        headers=auth_headers,
    )
    conv_id = create.json()["id"]
    resp = await client.get(f"/api/v1/conversations/{conv_id}", headers=auth_headers)
    assert resp.status_code == 200
    data = resp.json()
    assert len(data["messages"]) == 3
    assert data["messages"][0]["sender"] == "张三"


@pytest.mark.asyncio
async def test_update_title(client: AsyncClient, auth_headers: dict):
    create = await client.post(
        "/api/v1/conversations",
        json={"text": SAMPLE_CHAT},
        headers=auth_headers,
    )
    conv_id = create.json()["id"]
    resp = await client.patch(
        f"/api/v1/conversations/{conv_id}",
        json={"title": "新标题"},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    assert resp.json()["title"] == "新标题"


@pytest.mark.asyncio
async def test_delete_conversation(client: AsyncClient, auth_headers: dict):
    create = await client.post(
        "/api/v1/conversations",
        json={"text": SAMPLE_CHAT},
        headers=auth_headers,
    )
    conv_id = create.json()["id"]
    resp = await client.delete(f"/api/v1/conversations/{conv_id}", headers=auth_headers)
    assert resp.status_code == 204

    # Should be gone
    resp = await client.get(f"/api/v1/conversations/{conv_id}", headers=auth_headers)
    assert resp.status_code == 404


@pytest.mark.asyncio
async def test_user_isolation(client: AsyncClient, auth_headers: dict):
    """User A's conversations should not be visible to User B."""
    create = await client.post(
        "/api/v1/conversations",
        json={"text": SAMPLE_CHAT},
        headers=auth_headers,
    )
    conv_id = create.json()["id"]

    # Register a second user
    reg2 = await client.post(
        "/api/v1/auth/register",
        json={"username": "otheruser", "password": "pass123"},
    )
    headers2 = {"Authorization": f"Bearer {reg2.json()['token']}"}

    # User B cannot see User A's conversation
    resp = await client.get(f"/api/v1/conversations/{conv_id}", headers=headers2)
    assert resp.status_code == 404

    resp = await client.get("/api/v1/conversations", headers=headers2)
    assert resp.json()["total"] == 0


@pytest.mark.asyncio
async def test_title_too_long(client: AsyncClient, auth_headers: dict):
    long_title = "x" * 201
    resp = await client.post(
        "/api/v1/conversations",
        json={"text": SAMPLE_CHAT, "title": long_title},
        headers=auth_headers,
    )
    assert resp.status_code == 422


@pytest.mark.asyncio
async def test_not_found(client: AsyncClient, auth_headers: dict):
    resp = await client.get("/api/v1/conversations/nonexistent", headers=auth_headers)
    assert resp.status_code == 404
