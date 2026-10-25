import asyncio
from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import pytest
from langchain_core.messages import AIMessage, ToolMessage
from test_companion import request

from app.runtime.agent.agent import Agent


def response(source="history"):
    return AIMessage(
        content="",
        tool_calls=[
            dict(
                name="respond",
                id="finish",
                args=dict(label="PASS", hint="回应", facts=[dict(content="用户喜欢蓝色", source=source)]),
            )
        ],
    )


def setup(responses):
    model = MagicMock()
    model.bind_tools.return_value = model
    model.ainvoke = AsyncMock(side_effect=responses)
    service = SimpleNamespace(
        models=SimpleNamespace(agent=model, semaphores={"agent": asyncio.Semaphore(1)}),
        executor=SimpleNamespace(execute=AsyncMock()),
    )
    context = dict(request=request(content="我喜欢蓝色"), memoryIds=set(), toolUsage=[])
    return Agent(service), service, model, context


async def test_current_user_input_is_valid_conversation_evidence_on_first_turn():
    agent, service, model, context = setup([response()])
    directive = await agent.run(context)
    assert directive.label == "PASS" and directive.facts == []
    service.executor.execute.assert_not_awaited()
    assert model.ainvoke.await_count == 1


async def test_bad_final_directive_retries_without_replaying_executed_tool():
    tool = AIMessage(
        content="", tool_calls=[dict(name="recall", id="recall-1", args={"query": "喜欢的颜色"})]
    )
    agent, service, model, context = setup([tool, response("tool:foreign"), response("tool:recall-1")])

    async def execute(call, data):
        data["toolUsage"].append({"name": "recall", "success": True})
        return {"facts": ["用户喜欢蓝色"]}

    service.executor.execute.side_effect = execute
    assert (await agent.run(context)).facts[0].source == "tool:recall-1"
    assert service.executor.execute.await_count == 1
    assert model.ainvoke.await_count == 3


async def test_repeated_bad_directives_stop_after_one_correction():
    agent, service, model, context = setup([response("memory:999"), response("memory:999")])
    with pytest.raises(ValueError):
        await agent.run(context)
    assert model.ainvoke.await_count == 2
    service.executor.execute.assert_not_awaited()


async def test_schema_correction_receives_the_invalid_field():
    bad = response()
    bad.tool_calls[0]["args"]["scene"] = "UNKNOWN"
    agent, _, model, context = setup([bad, response()])
    assert (await agent.run(context)).label == "PASS"
    feedback = [m.content for m in model.ainvoke.await_args_list[1].args[0] if isinstance(m, ToolMessage)]
    assert any("scene" in item and "CHAT" in item for item in feedback)


async def test_excessive_tool_batch_has_no_side_effects():
    batch = AIMessage(
        content="", tool_calls=[dict(name="recall", id=f"r-{i}", args={"query": "颜色"}) for i in range(5)]
    )
    agent, service, model, context = setup([batch, batch])
    with pytest.raises(ValueError):
        await agent.run(context)
    service.executor.execute.assert_not_awaited()
    assert model.ainvoke.await_count == 2
