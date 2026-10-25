from __future__ import annotations

import json
from types import SimpleNamespace
from unittest.mock import AsyncMock

import httpx
from pydantic import SecretStr

from app.clients.cover_image import CoverImageClient
from app.runtime.models.gateway import ModelGateway
from app.schemas.models import CoverPlan


async def test_qwen_cover_uses_sync_contract_and_downloads_provider_image() -> None:
    calls: list[str] = []
    image_url = "https://dashscope-result.oss-cn-beijing.aliyuncs.com/cover.png"
    image_bytes = b"\x89PNG\r\n\x1a\n"

    def respond(request: httpx.Request) -> httpx.Response:
        calls.append(request.method)
        if request.method == "POST":
            body = json.loads(request.content)
            assert body["model"] == "qwen-image-3.0-pro"
            assert body["input"]["messages"][0]["content"] == [{"text": "水色天空和云朵"}]
            assert body["parameters"] == {
                "size": "1664*928",
                "n": 1,
                "watermark": True,
                "negative_prompt": "文字",
                "prompt_extend": False,
            }
            assert "X-DashScope-Async" not in request.headers
            return httpx.Response(
                200,
                json={"output": {"choices": [{"message": {"content": [{"image": image_url}]}}]}},
            )
        assert str(request.url) == image_url
        assert "Authorization" not in request.headers
        return httpx.Response(200, content=image_bytes)

    settings = SimpleNamespace(
        cover_model="qwen-image-3.0-pro",
        cover_url="https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
        api_key=SecretStr("unit-test"),
    )
    async with httpx.AsyncClient(transport=httpx.MockTransport(respond)) as http:
        gateway = ModelGateway(settings, AsyncMock())

        async def invoke(model, purpose, estimate, call, usage, retry=True):
            return await call()

        gateway.invoke = invoke
        result = await CoverImageClient(settings, http, gateway).generate(
            CoverPlan(prompt="水色天空和云朵", negative_prompt="文字")
        )
    assert result == image_bytes
    assert calls == ["POST", "GET"]
