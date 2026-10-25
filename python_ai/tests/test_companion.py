import asyncio
from contextlib import asynccontextmanager
from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import pytest

from app.runtime.agent.directive import Directive
from app.runtime.models.gateway import CURRENT, RoundUsage
from app.runtime.safety.judge import Judgment
from app.runtime.speech.parse import Parsed
from app.schemas.companion import ReplyRequest, Turn
from app.services.companion import Companion


def request(**overrides):
    values = dict(
        user_id=42,
        character="TSUNDERE",
        stage=1,
        nickname="云朵",
        content="今天有点累",
        tools=[],
        card={
            "name": "凛",
            "stages": [""] * 5,
            "levels": [{}] * 3,
            "fallback": "先歇一会儿。",
            "crisis": "联系现实里信任的人。",
        },
    )
    return ReplyRequest(**{**values, **overrides})


@asynccontextmanager
async def usage():
    yield SimpleNamespace(usage_metadata={})


def factory():
    model = MagicMock()
    model.bind_tools.return_value = model
    return SimpleNamespace(
        usage=usage,
        redis=AsyncMock(),
        agent=model,
        settings=SimpleNamespace(
            speech_model="speech", structured_method="function_calling", speculative=False
        ),
        semaphores={name: asyncio.Semaphore(1) for name in ("agent", "speech", "judge")},
    )


@pytest.mark.parametrize("label", ["CRISIS", "SEXUAL", "JAILBREAK"])
async def test_blocked_paths_never_generate_or_create_artifacts(label):
    service = Companion(SimpleNamespace(models=factory()), None)
    service.emotions = AsyncMock(return_value=[])
    service.agent.run = AsyncMock(return_value=Directive(label=label, hint="安全回应"))
    service.speak = AsyncMock(side_effect=AssertionError("不能调用嘴"))
    token = CURRENT.set(RoundUsage())
    try:
        result = await service.compute(request(message_allowed=False), AsyncMock())
    finally:
        CURRENT.reset(token)
    assert result["affection"] == 0 and not result["works"] and not result["drawings"]
    assert result["billable"] is False


async def test_missing_agent_result_ends_stream_with_error():
    service = Companion(SimpleNamespace(models=factory()), None)
    service.emotions = AsyncMock(return_value=[])
    service.agent.run = AsyncMock(side_effect=ValueError())
    events = [event async for event in service.stream(request())]
    assert [e["event"] for e in events] == ["error"]


async def test_recall_judgment_keeps_updated_fact_beyond_six_recent_rounds():
    models = factory()
    models.judge = MagicMock()
    checked = AsyncMock(return_value=dict(safe=True, character=8, artificial=1))
    models.judge.with_structured_output.return_value.ainvoke = checked
    service = Companion(SimpleNamespace(models=models), None)
    turns = []
    for index in range(28):
        content = "我搬到台北了。" if index == 16 else "普通闲聊。"
        turns.extend([Turn(role="USER", content=content), Turn(role="CHARACTER", content="嗯。")])
    await service.judge(
        request(content="我现在住在哪里？", history=turns),
        [],
        {"toolResults": [{"name": "recall", "success": True, "facts": []}]},
        Parsed(bubbles=["台中。"]),
    )
    assert any("我搬到台北了。" in str(message.content) for message in checked.await_args.args[0])


@pytest.mark.parametrize(
    "first",
    [
        Judgment(safe=False, character=5, artificial=1),
        Judgment(safe=True, character=5, artificial=8),
        Judgment(safe=True, character=5, artificial=1, unsupported=["编造过去"]),
    ],
)
async def test_safety_grounding_and_style_each_trigger_one_rewrite(first):
    service = Companion(SimpleNamespace(models=factory()), None)
    service.emotions = AsyncMock(return_value=[])
    service.agent.run = AsyncMock(return_value=Directive(label="PASS", hint="回应"))
    service.speak = AsyncMock(return_value=(Parsed(bubbles=["先歇一会儿。"]), {}))
    service.judge = AsyncMock(side_effect=[first, Judgment(safe=True, character=7, artificial=1)])
    token = CURRENT.set(RoundUsage())
    try:
        result = await service.compute(request(), AsyncMock())
    finally:
        CURRENT.reset(token)
    assert result["billable"] and result["metrics"]["rewrites"] == 1
    assert service.speak.await_count == 2


async def test_missing_judge_result_never_exposes_unchecked_text():
    service = Companion(SimpleNamespace(models=factory()), None)
    service.emotions = AsyncMock(return_value=[])
    service.agent.run = AsyncMock(return_value=Directive(label="PASS", hint="回应"))
    service.speak = AsyncMock(return_value=(Parsed(bubbles=["未经审核"]), {}))
    service.judge = AsyncMock(side_effect=RuntimeError())
    token = CURRENT.set(RoundUsage())
    try:
        result = await service.compute(request(), AsyncMock())
    finally:
        CURRENT.reset(token)
    assert result["bubbles"] == ["先歇一会儿。"] and not result["billable"]


async def test_actual_failed_tool_result_reaches_speech_and_both_judgments():
    service = Companion(SimpleNamespace(models=factory()), None)
    service.emotions = AsyncMock(return_value=[])
    actual = dict(id="toy-1", name="make_toy", success=False, facts=["工具执行失败，没有后台任务"])

    async def decide(context):
        context["toolResults"] = [actual]
        return Directive(label="PASS", hint="回应")

    service.agent.run = decide
    service.speak = AsyncMock(return_value=(Parsed(bubbles=["这次没做成。"]), {}))
    service.judge = AsyncMock(
        side_effect=[
            Judgment(safe=True, character=5, artificial=1, unsupported=["错误承诺后台任务"]),
            Judgment(safe=True, character=7, artificial=1),
        ]
    )
    token = CURRENT.set(RoundUsage())
    try:
        result = await service.compute(request(), AsyncMock())
    finally:
        CURRENT.reset(token)
    assert result["billable"] and result["metrics"]["rewrites"] == 1
    assert all(call.args[2]["toolResults"] == [actual] for call in service.speak.await_args_list)
    assert all(call.args[2]["toolResults"] == [actual] for call in service.judge.await_args_list)
