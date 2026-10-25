import asyncio
from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import pytest
from test_companion import factory, request

from app.runtime.agent.directive import Directive
from app.runtime.context.companion import messages
from app.runtime.models.gateway import CURRENT, RoundUsage
from app.runtime.safety.judge import Judgment
from app.runtime.speech.parse import Parsed
from app.services.companion import Companion


def test_speech_contains_natural_facts_and_no_machine_directive_or_empty_rows():
    body = request(
        daily="整理窗台上的小花盆",
        previous_feeling="有点困",
        controls=["reaction"],
        new_memories=[dict(id=57, content="喜欢蓝色", pinned=True)],
        card=dict(
            name="奶糖",
            brief="慵懒随性，有自己的节奏",
            stages=["刚认识"] * 5,
            levels=[dict(name="平静", description="回答比较简短", form=dict(question=0.123))] * 3,
            tics=[dict(text="喵", description="得意时偶尔带一句喵", frequency=[0.123] * 3)],
        ),
    )
    directive = dict(
        label="PASS",
        scene="CHAT",
        mention_memory_id=57,
        support_needed=False,
        facts=[dict(content="用户喜欢蓝色", source="memory:57")],
        hint="接着聊颜色",
    )
    result, _ = messages(body, [], directive)
    stable, snapshot, current = result[0].content, result[1].content, result[-1].content[0]["text"]
    assert "今天的你（背景，不用主动提，聊到了再说）：整理窗台上的小花盆" in snapshot
    assert "整理窗台" not in current
    assert "你想起来的：用户喜欢蓝色" in current and "这轮：接着聊颜色" in current
    assert "给他的消息挂个表情" in current
    for forbidden in ("PASS", "scene", "False", "57", "memory:", "reaction", "还没聊完的：", "到期的约定："):
        assert forbidden not in current
    assert "0.123" not in stable and "question" not in stable and "frequency" not in stable
    assert "回答比较简短" in stable and "得意时偶尔带一句喵" in stable
    assert "禁止表白" not in stable
    decision, _ = messages(body, [], speech=False)
    assert "57" in decision[-1].content[0]["text"]


def test_actual_failed_tool_state_is_natural_chinese():
    result, _ = messages(
        request(),
        [],
        dict(
            facts=[dict(source="tool:toy-1", content="已经提交后台")],
            toolResults=[dict(id="toy-1", name="make_toy", success=False, facts=["生成失败"])],
        ),
    )
    current = result[-1].content[0]["text"]
    assert "已经提交后台" not in current and "没有后台任务" in current and "生成失败" in current
    assert all(word not in current for word in ("toolResults", "make_toy", "success", "toy-1"))


def test_parallel_tool_facts_preserve_success_and_hide_recall_metadata():
    result, _ = messages(
        request(),
        [],
        dict(
            toolResults=[
                dict(id="one", name="web_search", success=False, facts=["没有找到"]),
                dict(id="two", name="draw_picture", success=True, facts=["已登记后台画图，完成后另行通知"]),
                dict(
                    id="three",
                    name="recall",
                    success=True,
                    facts=["id：57\npinned：True"],
                    speechFacts=["喜欢蓝色"],
                ),
            ]
        ),
    )
    current = result[-1].content[0]["text"]
    assert "已登记后台画图" in current and "这项操作没有后台任务" in current
    assert "你想起来的：喜欢蓝色" in current
    assert "57" not in current and "pinned" not in current and "True" not in current


def service():
    models = factory()
    models.settings.speculative = True
    result = Companion(SimpleNamespace(models=models), None)
    result.emotions = AsyncMock(return_value=[])
    result.judge = AsyncMock(return_value=Judgment(safe=True, character=8, artificial=1))
    return result


async def compute(value, body=None):
    token = CURRENT.set(RoundUsage())
    try:
        return await value.compute(body or request(), AsyncMock())
    finally:
        CURRENT.reset(token)


async def test_compact_judge_parses_omitted_defaults_and_has_system_priority():
    models = factory()
    model = MagicMock()
    model.with_structured_output.return_value.ainvoke = AsyncMock(
        return_value=dict(safe=True, character=8, artificial=1)
    )
    models.judge = model
    value = Companion(SimpleNamespace(models=models), None)
    review = await value.judge(request(), [], dict(label="PASS"), Parsed(bubbles=["嗯，坐会儿。 "]))
    assert review.safe and review.reason == "" and review.threads is None
    schema = model.with_structured_output.call_args.args[0]["parameters"]
    assert all("default" not in item for item in schema["properties"].values())
    inputs = model.with_structured_output.return_value.ainvoke.await_args.args[0]
    assert inputs[2].type == "system" and "最简示例" in inputs[2].content
    assert "最简示例" not in inputs[-1].content[0]["text"]


async def test_mouth_and_agent_start_together_and_plain_reply_is_adopted():
    value = service()
    mouth_started, agent_started = asyncio.Event(), asyncio.Event()

    async def speak(*_):
        mouth_started.set()
        await agent_started.wait()
        return Parsed(bubbles=["嗯，坐会儿。"]), {}

    async def decide(_):
        agent_started.set()
        await mouth_started.wait()
        return Directive(label="PASS")

    value.agent.run, value.speak = decide, AsyncMock(side_effect=speak)
    result = await asyncio.wait_for(compute(value, request(threads=["周五的面试"])), 1)
    assert result["bubbles"] == ["嗯，坐会儿。"] and result["billable"]
    assert result["metrics"]["speculativeAdopted"] and value.speak.await_count == 1
    assert result["threads"] == ["周五的面试"]  # 省略threads不能清空已有话题。


@pytest.mark.parametrize("label", ["ROMANCE_PUSH", "PASS"])
async def test_support_identity_and_romance_reuse_preflight_but_still_judge(label):
    value = service()
    value.agent.run = AsyncMock(
        return_value=Directive(label=label, support_needed=True, identity_question=True)
    )
    value.speak = AsyncMock(return_value=(Parsed(bubbles=["我是AI角色，和你做朋友。"]), {}))
    result = await compute(value)
    assert result["metrics"]["speculativeAdopted"] and value.speak.await_count == 1
    value.judge.assert_awaited_once()
    if label == "ROMANCE_PUSH":
        assert result["affection"] == 0


@pytest.mark.parametrize("label", ["CRISIS", "SEXUAL", "JAILBREAK"])
async def test_unsafe_input_discards_preflight_and_never_emits_its_text(label):
    value = service()
    value.agent.run = AsyncMock(return_value=Directive(label=label))
    value.speak = AsyncMock(return_value=(Parsed(bubbles=["不应发送的预跑内容"]), {}))
    result = await compute(value)
    assert "不应发送" not in str(result["bubbles"])
    assert not result["metrics"]["speculativeAdopted"] and not result["billable"]
    value.judge.assert_not_awaited()


async def test_tool_reply_discards_preflight_and_keeps_both_usage_records():
    value = service()
    started = asyncio.Event()

    async def decide(context):
        await started.wait()
        context["toolResults"] = [dict(id="search-1", name="web_search", success=True)]
        return Directive(label="PASS", facts=[dict(content="查到周六开放", source="tool:search-1")])

    async def speak(_, __, instruction):
        started.set()
        CURRENT.get().calls.append(dict(purpose="speech", output=1, cached=0))
        return Parsed(bubbles=["查到了。" if instruction else "还没查过。"]), {}

    value.agent.run, value.speak = decide, AsyncMock(side_effect=speak)
    result = await compute(value)
    assert result["bubbles"] == ["查到了。"] and not result["metrics"]["speculativeAdopted"]
    assert len(result["metrics"]["calls"]) == value.speak.await_count == 2


async def test_disabled_preflight_waits_for_agent_and_disconnect_cleans_child():
    value = service()
    value.models.settings.speculative = False
    value.agent.run = AsyncMock(return_value=Directive(label="PASS"))
    value.speak = AsyncMock(return_value=(Parsed(bubbles=["嗯"]), {}))
    result = await compute(value)
    assert not result["metrics"]["speculativeStarted"] and value.speak.await_count == 1
    value.models.settings.speculative = True
    started, ended = asyncio.Event(), asyncio.Event()

    async def speak(*_):
        started.set()
        try:
            await asyncio.Event().wait()
        finally:
            ended.set()

    async def decide(_):
        await asyncio.Event().wait()

    value.speak, value.agent.run = speak, decide
    pending = asyncio.create_task(compute(value))
    await asyncio.wait_for(started.wait(), 1)
    pending.cancel()
    with pytest.raises(asyncio.CancelledError):
        await pending
    assert ended.is_set()
