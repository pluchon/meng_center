from unittest.mock import AsyncMock

import httpx
import pytest
from langchain_core.messages import AIMessage
from langchain_core.outputs import ChatGeneration, ChatResult
from langchain_openai import ChatOpenAI
from pydantic import SecretStr

from app.clients.models import ModelFactory
from app.config.settings import Settings
from app.runtime.models.gateway import GatewayChatOpenAI, ModelGateway, ModelUnavailable


async def test_speech_fallbacks_keep_each_provider_credentials_and_protocol(monkeypatch):
    settings = Settings.model_construct(
        api_key=SecretStr("dashscope-fixture"), deepseek_api_key=SecretStr("deepseek-fixture")
    )
    redis = AsyncMock()
    redis.exists.return_value = False
    redis.eval.return_value = 1
    redis.incr.return_value = 1
    attempts = []

    async def generate(client, *args, **kwargs):
        attempts.append(
            (
                client.model_name,
                client.openai_api_base,
                client.openai_api_key.get_secret_value(),
                client.extra_body,
            )
        )
        if client.model_name.startswith("deepseek-"):
            raise TimeoutError()
        return ChatResult(generations=[ChatGeneration(message=AIMessage(content="收到。"))])

    monkeypatch.setattr(ChatOpenAI, "_agenerate", generate)
    async with httpx.AsyncClient() as http:
        factory = ModelFactory(settings, redis, http)
        assert (await factory.speech.ainvoke("你好")).content == "收到。"
        assert factory.agent.openai_api_key.get_secret_value() == "dashscope-fixture"
    assert [item[0] for item in attempts] == [
        "deepseek-flash",
        "deepseek-flash",
        "deepseek-v4-pro",
        "deepseek-v4-pro",
        "qwen3.8-flash",
    ]
    for model, url, key, body in attempts:
        if model.startswith("deepseek-"):
            assert (url, key, body) == (
                "https://api.deepseek.com",
                "deepseek-fixture",
                {"thinking": {"type": "disabled"}},
            )
        else:
            assert (url, key, body) == (settings.base_url, "dashscope-fixture", {"enable_thinking": False})
    accounts = {call.args[2] for call in redis.eval.await_args_list}
    assert accounts == {"meng:ai:ratelimit:account:deepseek", "meng:ai:ratelimit:account:dashscope"}


async def test_missing_official_key_fails_before_request_and_does_not_reuse_dashscope():
    gateway = ModelGateway(Settings.model_construct(api_key=SecretStr("fixture")), AsyncMock())
    with pytest.raises(ModelUnavailable, match="DeepSeek"):
        await gateway.acquire("deepseek-flash", 10)
    gateway.redis.eval.assert_not_awaited()


def test_official_cache_usage_preserves_total_and_cached_tokens():
    result = ChatResult(
        generations=[
            ChatGeneration(
                message=AIMessage(
                    content="收到。", usage_metadata=dict(input_tokens=100, output_tokens=8, total_tokens=108)
                )
            )
        ],
        llm_output={"token_usage": {"prompt_cache_hit_tokens": 64}},
    )
    assert GatewayChatOpenAI.chat_usage(result) == dict(
        input_tokens=100, output_tokens=8, total_tokens=108, input_token_details={"cache_read": 64}
    )


@pytest.mark.parametrize("size,expected", [(1000, False), (7000, True)])
async def test_speech_reports_full_request_window_pressure(monkeypatch, size, expected):
    settings = Settings.model_construct(
        api_key=SecretStr("fixture"),
        deepseek_api_key=SecretStr("fixture"),
        model_overrides={"speech": {"context_tokens": 10000}},
    )
    redis = AsyncMock()
    redis.exists.return_value = False
    redis.eval.return_value = 1

    async def generate(*args, **kwargs):
        return ChatResult(generations=[ChatGeneration(message=AIMessage(content="收到。"))])

    monkeypatch.setattr(ChatOpenAI, "_agenerate", generate)
    factory = ModelFactory(settings, redis, http=AsyncMock())
    result = await factory.speech.ainvoke("甲" * size)
    budget = result.response_metadata["contextBudget"]
    assert budget["limit"] == 10000 and budget["compactAfterRound"] == expected
    assert budget["estimated"] < budget["limit"]


async def test_parallel_calls_keep_their_own_segment_metrics_without_shared_fallback(monkeypatch):
    import asyncio

    from app.runtime.models.gateway import CURRENT, RoundUsage

    settings = Settings.model_construct(
        api_key=SecretStr("fixture"), deepseek_api_key=SecretStr("fixture")
    )
    redis = AsyncMock()
    redis.exists.return_value = False
    redis.eval.return_value = 1
    entered = 0
    ready = asyncio.Event()

    async def generate(*args, **kwargs):
        nonlocal entered
        entered += 1
        if entered == 3:
            ready.set()
        await asyncio.wait_for(ready.wait(), timeout=2)
        return ChatResult(generations=[ChatGeneration(message=AIMessage(
            content="收到。", usage_metadata=dict(input_tokens=100, output_tokens=1, total_tokens=101)
        ))])

    monkeypatch.setattr(ChatOpenAI, "_agenerate", generate)
    factory = ModelFactory(settings, redis, http=AsyncMock())
    current = RoundUsage(segments={"history": 999})
    token = CURRENT.set(current)
    try:
        await asyncio.gather(
            factory.agent.ainvoke("审核", config={"metadata": {"contextSegments": {"history": 20}}}),
            factory.speech.ainvoke("接话", config={"metadata": {"contextSegments": {"history": 80}}}),
            factory.judge.ainvoke("检查"),
        )
    finally:
        CURRENT.reset(token)
    assert {row["purpose"]: row["segments"] for row in current.calls} == {
        "agent": {"history": 20}, "speech": {"history": 80}, "judge": {},
    }
    assert current.tokens == 303 and len(current.calls) == 3
    current.segments["history"] = 0
    assert next(row for row in current.calls if row["purpose"] == "speech")["segments"] == {"history": 80}
