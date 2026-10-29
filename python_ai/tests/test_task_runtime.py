from pathlib import Path
from unittest.mock import AsyncMock

import pytest
from langchain_core.messages import AIMessage
from langchain_core.outputs import ChatGeneration, ChatResult
from langchain_openai import ChatOpenAI
from pydantic import SecretStr, ValidationError

from app.clients.models import ModelFactory
from app.config.settings import Settings, TaskOptions
from app.runtime.models.gateway import CURRENT, ModelUnavailable, RoundUsage
from app.runtime.tasks.registry import configured
from app.runtime.tasks.structured import REVIEW_UNAVAILABLE, TaskRuntime
from app.schemas.models import Moderation


def models(**overrides):
    settings = Settings.model_construct(
        api_key=SecretStr("fixture"), **overrides
    )
    redis = AsyncMock()
    redis.exists.return_value = False
    redis.eval.return_value = 1
    redis.incr.return_value = 1
    return ModelFactory(settings, redis, http=AsyncMock())


def response(schema="Moderation", **values):
    return ChatResult(
        generations=[
            ChatGeneration(
                message=AIMessage(
                    content="",
                    tool_calls=[{"name": schema, "args": values or {"verdict": "PASS"}, "id": "fixture"}],
                )
            )
        ]
    )


async def test_task_options_reach_model_and_usage_metadata_without_private_sdk_parameters(monkeypatch):
    factory = models(task_overrides={"moderate_text": TaskOptions(temperature=0.4, timeout=4, retries=0)})
    captured = []

    async def generate(client, messages, **kwargs):
        captured.append(kwargs)
        return response()

    monkeypatch.setattr(ChatOpenAI, "_agenerate", generate)
    usage = RoundUsage()
    token = CURRENT.set(usage)
    try:
        result = await TaskRuntime(factory).chain("moderate_text").ainvoke({"payload": "公开内容"})
    finally:
        CURRENT.reset(token)
    assert result.verdict == "PASS" and len(captured) == 1
    assert captured[0]["temperature"] == 0.4 and captured[0]["timeout"] == 4
    assert "_runtime_retries" not in captured[0]
    assert usage.calls[0]["task"] == "moderate_text"
    assert usage.calls[0]["taskVersion"] and usage.calls[0]["evaluationSet"] == "evals/moderation.jsonl"


@pytest.mark.parametrize("retries", [0, 1])
async def test_task_retry_limit_controls_network_attempts(monkeypatch, retries):
    factory = models(task_overrides={"moderate_text": TaskOptions(retries=retries)})
    generate = AsyncMock(side_effect=TimeoutError("offline"))
    monkeypatch.setattr(ChatOpenAI, "_agenerate", generate)
    with pytest.raises(ModelUnavailable):
        await TaskRuntime(factory).chain("moderate_text").ainvoke({"payload": "公开内容"})
    assert generate.await_count == retries + 1


async def test_compact_judgment_validation_retries_only_the_invalid_reply(monkeypatch):
    factory = models()
    generate = AsyncMock(
        side_effect=[
            response("Judgment", safe=True, character=8, artificial=99),
            response("Judgment", safe=True, character=8, artificial=1),
        ]
    )
    monkeypatch.setattr(ChatOpenAI, "_agenerate", generate)
    result = await TaskRuntime(factory).output("reply_judge").ainvoke("检查回复")
    assert result.artificial == 1 and generate.await_count == 2


@pytest.mark.parametrize("fallback", ["suspect", "unavailable"])
async def test_registered_review_fallback_is_applied(monkeypatch, fallback):
    factory = models(task_overrides={"moderate_confirm": TaskOptions(fallback=fallback)})
    monkeypatch.setattr(ChatOpenAI, "_agenerate", AsyncMock(side_effect=RuntimeError("offline")))
    chain = TaskRuntime(factory).output("moderate_confirm")
    if fallback == "suspect":
        result = await chain.ainvoke("目标内容")
        assert result.verdict == "SUSPECT" and result.reason == REVIEW_UNAVAILABLE
    else:
        with pytest.raises(ModelUnavailable):
            await chain.ainvoke("目标内容")


def test_configuration_precedence_and_invalid_configuration_are_checked_before_calls():
    factory = models(
        model_overrides={"judge": {"temperature": 0.3, "timeout": 7}},
        task_overrides={"moderate_text": TaskOptions(timeout=4)},
    )
    specs = configured(factory.settings)
    assert specs["understand"].temperature == 0.3 and specs["understand"].timeout == 7
    assert specs["moderate_text"].temperature == 0.3 and specs["moderate_text"].timeout == 4
    assert all(Path(spec.evaluation).is_file() for spec in specs.values())
    with pytest.raises(ValueError, match="未知任务"):
        configured(Settings.model_construct(task_overrides={"unknown": {}}))
    with pytest.raises(ValueError, match="BLOCK"):
        configured(Settings.model_construct(task_overrides={"tags": TaskOptions(fallback="suspect")}))
    with pytest.raises(ValueError, match="唯一"):
        TaskRuntime(factory).name_for_schema(Moderation)
    with pytest.raises(ValidationError):
        TaskOptions(retries=2)
    with pytest.raises(ValidationError):
        TaskOptions(unknown=True)
