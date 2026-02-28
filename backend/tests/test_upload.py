"""Upload endpoint tests."""
import io
import pytest
from httpx import AsyncClient

from tests.conftest import SAMPLE_CHAT


@pytest.mark.asyncio
async def test_upload_text_only(client: AsyncClient, auth_headers: dict):
    resp = await client.post(
        "/api/v1/conversations/upload",
        data={"text": SAMPLE_CHAT},
        headers=auth_headers,
    )
    assert resp.status_code == 201
    assert resp.json()["message_count"] == 3


@pytest.mark.asyncio
async def test_upload_with_title(client: AsyncClient, auth_headers: dict):
    resp = await client.post(
        "/api/v1/conversations/upload",
        data={"text": SAMPLE_CHAT, "title": "上传测试"},
        headers=auth_headers,
    )
    assert resp.status_code == 201
    assert resp.json()["title"] == "上传测试"


@pytest.mark.asyncio
async def test_upload_with_small_image(client: AsyncClient, auth_headers: dict):
    # Create a tiny fake image (1x1 PNG-like bytes)
    fake_img = b"\x89PNG" + b"\x00" * 100
    resp = await client.post(
        "/api/v1/conversations/upload",
        data={"text": SAMPLE_CHAT},
        files=[("images", ("test.png", io.BytesIO(fake_img), "image/png"))],
        headers=auth_headers,
    )
    assert resp.status_code == 201
