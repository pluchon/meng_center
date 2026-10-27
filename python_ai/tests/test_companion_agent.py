from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from langchain_core.messages import AIMessage
from test_companion import factory, request

from app.runtime.agent.agent import Agent


def response(source="history"):
    return AIMessage(
        content="",
        tool_calls=[
            dict(
                name="Response",
                id="finish",
                args=dict(label="PASS", facts=[dict(content="用户喜欢蓝色", source=source)]),
            )
        ],
    )


def setup(responses):
    models = factory()
    models.agent.messages = iter(responses)
    service = SimpleNamespace(models=models, executor=SimpleNamespace(execute=AsyncMock()))
    context = dict(service=service, request=request(), memoryIds=set(), toolUsage=[], toolResults=[])
    return Agent(service), service, context


async def test_current_input_is_valid_evidence_and_chat_omits_facts():
    agent, service, context = setup([response()])
    directive = await agent.run(context)
    assert directive.label == "PASS" and directive.facts == []
    service.executor.execute.assert_not_awaited()


async def test_invalid_sources_retry_structured_response_without_repeating_tools():
    call = AIMessage(content="", tool_calls=[dict(name="recall", id="r1", args={"query": "喜欢的颜色"})])
    agent, service, context = setup([call, response("tool:foreign"), response("tool:r1")])
    service.executor.execute.return_value = dict(id="r1", facts=["喜欢蓝色"])
    assert (await agent.run(context)).facts[0].source == "tool:r1"
    service.executor.execute.assert_awaited_once()


async def test_repeated_invalid_directives_fail_after_one_correction():
    agent, service, context = setup([response("memory:999"), response("memory:999")])
    with pytest.raises(ValueError):
        await agent.run(context)
    service.executor.execute.assert_not_awaited()


async def test_schema_validation_uses_tool_strategy_retry():
    bad = response()
    bad.tool_calls[0]["args"]["scene"] = "UNKNOWN"
    agent, _, context = setup([bad, response()])
    assert (await agent.run(context)).label == "PASS"


async def test_missing_structured_response_fails_closed():
    agent, _, context = setup([AIMessage(content="未结束")] * 5)
    with pytest.raises(Exception, match="缺少说话指令"):
        await agent.run(context)
