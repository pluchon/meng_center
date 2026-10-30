from unittest.mock import AsyncMock

import httpx
import pytest
from langchain_core.messages import AIMessage
from langchain_core.outputs import ChatGeneration, ChatResult
from langchain_openai import ChatOpenAI
from pydantic import SecretStr

from app.clients.models import ModelFactory
from app.config.settings import Settings
from app.runtime.models.gateway import GatewayChatOpenAI, ModelGateway


async def test_speech_fallbacks_share_dashscope_credentials_and_protocol(monkeypatch):
    settings = Settings.model_construct(
        api_key=SecretStr("dashscope-fixture")
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
        "deepseek-v4.1-flash-1017", "deepseek-v4.1-flash-1017", "qwen3.8-flash",
    ]
    for _, url, key, body in attempts:
        assert (url, key, body) == (settings.base_url, "dashscope-fixture", {"enable_thinking": False})
    accounts = {call.args[2] for call in redis.eval.await_args_list}
    assert accounts == {"meng:ai:ratelimit:account:dashscope"}


@pytest.mark.parametrize("purpose,model,explicit", [
    ("agent", "qwen3.8-flash", True), ("judge", "qwen3.8-flash", False),
    ("speech", "deepseek-v4.1-flash-1017", False), ("agent", "other-model", False),
])
async def test_explicit_cache_wire_format_preserves_messages_and_tool_definitions(purpose, model, explicit):
    import json

    from langchain_core.messages import HumanMessage, SystemMessage

    settings = Settings.model_construct(
        api_key=SecretStr("fixture")
    )
    redis = AsyncMock()
    redis.exists.return_value = False
    redis.eval.return_value = 1
    captured = []

    def respond(request):
        captured.append(json.loads(request.content))
        return httpx.Response(200, json=dict(
            id="offline", object="chat.completion", created=0, model=model,
            choices=[dict(index=0, finish_reason="stop", message=dict(role="assistant", content="收到。"))],
            usage=dict(prompt_tokens=1700, completion_tokens=1, total_tokens=1701,
                       prompt_tokens_details=dict(cached_tokens=1000, cache_creation_input_tokens=500)),
        ))

    messages = [SystemMessage("固定审核规则。" * 300), HumanMessage("原始用户输入")]
    tools = [dict(type="function", function=dict(name="read_post", description="读取帖子",
                 parameters=dict(type="object", properties={}, required=[])))]
    async with httpx.AsyncClient(transport=httpx.MockTransport(respond)) as client:
        gateway = ModelGateway(settings, redis)
        chat = GatewayChatOpenAI(model=model, api_key="fixture", base_url="https://offline.invalid/v1",
                                 http_async_client=client, max_retries=0).configure_gateway(gateway, purpose)
        await chat.bind_tools(tools).ainvoke(messages)
    first = captured[0]["messages"][0]["content"]
    assert first == ([dict(type="text", text=messages[0].content,
                          cache_control=dict(type="ephemeral"))] if explicit else messages[0].content)
    assert captured[0]["messages"][1] == dict(role="user", content="原始用户输入")
    assert captured[0]["tools"] == tools
    assert isinstance(messages[0].content, str)


def test_cache_creation_usage_is_preserved_separately_from_actual_cache_hits():
    result = ChatResult(generations=[ChatGeneration(message=AIMessage(
        content="收到。", usage_metadata=dict(input_tokens=1700, output_tokens=1, total_tokens=1701,
                                             input_token_details=dict(cache_read=1000)),
    ))], llm_output=dict(token_usage=dict(prompt_tokens_details=dict(cache_creation_input_tokens=500))))
    usage = GatewayChatOpenAI.chat_usage(result)
    assert usage["input_tokens"] == 1700 and usage["output_tokens"] == 1
    assert usage["input_token_details"] == dict(cache_read=1000, cache_creation=500)


@pytest.mark.parametrize("reasoning", [None, "", "不可保存的思考正文"])
async def test_bailian_reasoning_observation_survives_sdk_conversion_without_saving_text(reasoning):
    import json

    from app.runtime.models.gateway import CURRENT, RoundUsage

    settings = Settings.model_construct(api_key=SecretStr("fixture"))
    redis = AsyncMock()
    redis.exists.return_value, redis.eval.return_value = False, 1
    payloads = []

    def respond(request):
        payloads.append(json.loads(request.content))
        return httpx.Response(200, json=dict(
            id="offline", object="chat.completion", created=0, model=settings.speech_model,
            choices=[dict(index=0, finish_reason="stop", message=dict(
                role="assistant", content="收到。", reasoning_content=reasoning,
            ))], usage=dict(prompt_tokens=10, completion_tokens=2, total_tokens=12),
        ))

    current = RoundUsage()
    token = CURRENT.set(current)
    try:
        async with httpx.AsyncClient(transport=httpx.MockTransport(respond)) as http:
            models = ModelFactory(settings, redis, http)
            client = models.speech.root_async_client.with_options(http_client=http)
            models.speech.async_client = client.chat.completions
            result = await models.speech.ainvoke("你好")
    finally:
        CURRENT.reset(token)
    assert payloads[0]["enable_thinking"] is False
    assert result.response_metadata["reasoningContentPresent"] is bool(reasoning)
    assert current.calls[0]["reasoningContentPresent"] is bool(reasoning)
    assert current.calls[0]["reasoningTokens"] is None
    assert "不可保存的思考正文" not in result.model_dump_json()
    assert "不可保存的思考正文" not in json.dumps(current.calls, ensure_ascii=False)


@pytest.mark.parametrize("thinking", [False, True])
def test_responses_reasoning_blocks_are_observed_even_with_an_empty_summary(thinking):
    blocks = [dict(type="text", text="收到。")]
    if thinking:
        blocks.insert(0, dict(type="reasoning", summary=[]))
    result = ChatResult(generations=[ChatGeneration(message=AIMessage(content=blocks))])
    assert GatewayChatOpenAI.chat_usage(result, responses=True)["reasoning_content_present"] is thinking
    assert GatewayChatOpenAI.chat_usage(result)["reasoning_content_present"] is None


@pytest.mark.parametrize("size,expected", [(1000, False), (7000, True)])
async def test_speech_reports_full_request_window_pressure(monkeypatch, size, expected):
    settings = Settings.model_construct(
        api_key=SecretStr("fixture"),
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
        api_key=SecretStr("fixture")
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
