"""Zhipu GLM LLM client."""
import asyncio
from zhipuai import ZhipuAI
from app.config import settings

_client: ZhipuAI | None = None


def _get_client() -> ZhipuAI:
    global _client
    if _client is None:
        if not settings.zhipu_api_key:
            raise RuntimeError("WECHATMEM_ZHIPU_API_KEY not set")
        _client = ZhipuAI(api_key=settings.zhipu_api_key)
    return _client


def _sync_generate(prompt: str, system: str) -> str:
    client = _get_client()
    messages = []
    if system:
        messages.append({"role": "system", "content": system})
    messages.append({"role": "user", "content": prompt})
    response = client.chat.completions.create(
        model=settings.zhipu_model, messages=messages
    )
    return response.choices[0].message.content


async def generate_text(prompt: str, system: str = "") -> str:
    return await asyncio.to_thread(_sync_generate, prompt, system)


async def ask_with_context(question: str, context: str) -> str:
    system = "你是一个微信聊天记录助手。根据提供的聊天记录上下文回答用户问题。如果上下文中没有相关信息，请如实说明。"
    prompt = f"聊天记录上下文：\n{context}\n\n用户问题：{question}"
    return await generate_text(prompt, system)
