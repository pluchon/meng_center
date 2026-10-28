from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from langchain_core.messages import AIMessage
from test_companion import factory, request

from app.runtime.agent.agent import Agent, Context


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
    context["request"].recalled = [{"id": 101, "content": "喜欢蓝色"}]
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


async def test_search_then_read_can_finish_within_three_model_calls():
    search = AIMessage(content="", tool_calls=[dict(name="search_posts", id="s1", args={"query": "新番"})])
    read = AIMessage(content="", tool_calls=[dict(name="read_post", id="p1", args={"post_id": 101})])
    agent, service, context = setup([search, read, response("tool:p1")])
    service.executor.execute.side_effect = [
        dict(id="s1", facts=["新番帖子"], sources=[dict(postId=101, title="新番讨论")]),
        dict(id="p1", facts=["帖子正文"]),
    ]
    assert (await agent.run(context)).facts[0].source == "tool:p1"
    assert service.executor.execute.await_count == 2


@pytest.mark.parametrize("name", ["my_recent_posts", "my_growth"])
async def test_empty_argument_tools_receive_runtime_context(name):
    from app.runtime.tools.registry import TOOLS, declare

    assert declare(TOOLS[name]).tool_call_schema.model_json_schema()["properties"] == {}
    call = AIMessage(content="", tool_calls=[dict(name=name, id="empty1", args={})])
    agent, service, context = setup([call, response("tool:empty1")])
    service.executor.execute.return_value = dict(id="empty1", facts=["只读结果"])
    assert (await agent.run(context)).facts[0].source == "tool:empty1"
    assert service.executor.execute.await_args.args[0] == dict(id="empty1", name=name, args={})


async def test_last_call_excludes_business_tools_and_keeps_structured_response():
    from langchain_core.language_models.fake_chat_models import GenericFakeChatModel

    bindings, inputs = [], []

    class RecordingModel(GenericFakeChatModel):
        def bind_tools(self, tools, **kwargs):
            bindings.append([tool.name for tool in tools])
            return self

        def _generate(self, messages, *args, **kwargs):
            inputs.append(messages)
            return super()._generate(messages, *args, **kwargs)

    search = AIMessage(content="", tool_calls=[dict(name="search_posts", id="s1", args={"query": "新番"})])
    read = AIMessage(content="", tool_calls=[dict(name="read_post", id="p1", args={"post_id": 101})])
    _, service, context = setup([])
    service.models.agent = RecordingModel(messages=iter([search, read, response("tool:p1")]))
    service.executor.execute.side_effect = [dict(id="s1", facts=[]), dict(id="p1", facts=["正文"])]
    await Agent(service).run(context)
    assert "search_posts" in bindings[0] and "read_post" in bindings[1]
    assert bindings[2] == ["Response"]
    assert '"source": "tool:p1"' in inputs[2][-1].content


@pytest.mark.parametrize("content", ["就一天而已，应该也没什么吧？", "你还记得我喜欢什么颜色吗？"])
async def test_memory_tool_availability_is_scoped_to_each_request(content):
    from langchain_core.language_models.fake_chat_models import GenericFakeChatModel

    bindings = []

    class RecordingModel(GenericFakeChatModel):
        def bind_tools(self, tools, **kwargs):
            bindings.append({tool.name for tool in tools})
            return self

    _, service, context = setup([])
    service.models.agent = RecordingModel(messages=iter([response()] * 3))
    context["request"].content = content
    agent = Agent(service)
    await agent.run(context)
    context["request"].recalled = [{"id": 101, "content": "不会直接进入提示词的记忆"}]
    await agent.run(context)
    context["request"].recalled = []
    await agent.run(context)
    assert "recall" not in bindings[0] and "recall" in bindings[1] and "recall" not in bindings[2]
    assert all(
        {"Response", "search_posts", "read_post", "my_recent_posts", "my_growth"} <= tools
        for tools in bindings
    )
    service.executor.execute.assert_not_awaited()


async def test_unavailable_recall_is_rejected_without_hiding_the_attempt():
    from langchain_core.messages import ToolMessage

    call = AIMessage(content="", tool_calls=[dict(name="recall", id="r1", args={"query": "一天"})])
    agent, service, context = setup([call, response()])
    result = await agent.graph.ainvoke({"messages": []}, context=Context(context))
    error = next(
        message for message in result["messages"]
        if isinstance(message, ToolMessage) and message.tool_call_id == "r1"
    )
    assert error.status == "error" and "没有可召回的记忆候选" in error.content
    assert context["toolUsage"] == [
        dict(name="recall", success=False, rejected=True, cached=False, elapsedMs=0)
    ]
    assert context["toolResults"] == [] and context["memoryIds"] == set()
    service.executor.execute.assert_not_awaited()
