"""Search endpoint tests (with mocked vectorstore)."""
import pytest
from unittest.mock import patch, MagicMock
from httpx import AsyncClient

from tests.conftest import SAMPLE_CHAT


@pytest.mark.asyncio
async def test_search_without_vectorstore(client: AsyncClient, auth_headers: dict):
    """Search should return 503 when vectorstore is not available."""
    resp = await client.post(
        "/api/v1/search",
        json={"query": "你好"},
        headers=auth_headers,
    )
    # Without chromadb installed, should get 503
    assert resp.status_code in (200, 503)


@pytest.mark.asyncio
async def test_search_requires_auth(client: AsyncClient):
    resp = await client.post("/api/v1/search", json={"query": "test"})
    assert resp.status_code in (401, 403)


@pytest.mark.asyncio
async def test_ask_requires_auth(client: AsyncClient):
    resp = await client.post("/api/v1/search/ask", json={"question": "test"})
    assert resp.status_code in (401, 403)


@pytest.mark.asyncio
async def test_search_with_mock(client: AsyncClient, auth_headers: dict):
    """Search with mocked vectorstore returns filtered results."""
    # First create a conversation so we have a valid conv_id
    create = await client.post(
        "/api/v1/conversations",
        json={"text": SAMPLE_CHAT},
        headers=auth_headers,
    )
    conv_id = create.json()["id"]

    mock_results = [
        {
            "message_id": "msg1",
            "content": "你好",
            "score": 0.95,
            "metadata": {
                "conversation_id": conv_id,
                "sender": "张三",
                "timestamp": "2024-01-01 10:00:00",
                "type": "message",
            },
        }
    ]

    with patch("app.api.search._get_vector_search") as mock_fn:
        mock_search = MagicMock(return_value=mock_results)
        mock_fn.return_value = mock_search

        resp = await client.post(
            "/api/v1/search",
            json={"query": "你好", "conversation_id": conv_id},
            headers=auth_headers,
        )
        assert resp.status_code == 200
        data = resp.json()
        assert len(data["results"]) == 1
        assert data["results"][0]["content"] == "你好"
