import asyncio
from contextlib import asynccontextmanager
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from langchain_core.language_models.fake_chat_models import GenericFakeChatModel

from app.runtime.agent.directive import Directive
from app.runtime.safety.judge import Judgment
from app.runtime.speech.parse import Parsed
from app.schemas.companion import ReplyRequest
from app.services.companion import Companion


def request(**overrides):
    values = dict(
        user_id=42,
        character="NEKO",
        nickname="云朵",
        content="今天有点累",
        card={
            "name": "奶糖",
            "fallback": "先歇一会儿。",
            "crisis": "联系现实里信任的人。",
        },
    )
    return ReplyRequest(**{**values, **overrides})


@asynccontextmanager
async def usage():
    yield SimpleNamespace(usage_metadata={})


def factory():
    class FakeModel(GenericFakeChatModel):
        def bind_tools(self, tools, **kwargs):
            return self

    model = FakeModel(messages=iter([]))
    return SimpleNamespace(
        usage=usage,
        redis=AsyncMock(),
        agent=model,
        settings=SimpleNamespace(speech_model="speech", structured_method="function_calling"),
        semaphores={name: asyncio.Semaphore(1) for name in ("agent", "speech", "judge")},
    )


def service():
    value = Companion(SimpleNamespace(models=factory()), None)
    value.emotions = AsyncMock(return_value=[])
    value.agent.run = AsyncMock(return_value=Directive(label="PASS"))
    value.speak = AsyncMock(return_value=(Parsed(bubbles=["嗯，先歇一会儿。"]), {}))
    value.judge = AsyncMock(return_value=Judgment(safe=True, character=8, artificial=1))
    return value


async def test_chat_adopts_prejudged_draft():
    value = service()
    result = await value.compute(request(), AsyncMock())
    assert result["billable"] and result["metrics"]["speculativeAdopted"]
    assert value.speak.await_count == value.judge.await_count == 1


@pytest.mark.parametrize("label", ["CRISIS", "SEXUAL", "JAILBREAK"])
async def test_blocked_paths_wait_for_branches_and_only_emit_fixed_reply(label):
    value = service()
    value.agent.run = AsyncMock(return_value=Directive(label=label))
    result = await value.compute(request(), AsyncMock())
    assert result["model"] == "fixed"
    assert result["billable"] == (label != "CRISIS")
    assert "嗯，先歇一会儿。" not in result["bubbles"]


async def test_missing_agent_result_ends_stream_with_error():
    value = service()
    value.agent.run = AsyncMock(side_effect=ValueError())
    assert [e["event"] async for e in value.stream(request())] == ["error"]


@pytest.mark.parametrize(
    "first",
    [
        Judgment(safe=False, character=5, artificial=1),
        Judgment(safe=True, character=5, artificial=8),
        Judgment(safe=True, character=5, artificial=1, unsupported=["编造过去"]),
    ],
)
async def test_rejected_draft_is_rewritten_once(first):
    value = service()
    value.judge.side_effect = [first, Judgment(safe=True, character=8, artificial=1)]
    result = await value.compute(request(), AsyncMock())
    assert result["billable"] and result["metrics"]["rewrites"] == 1
    assert value.speak.await_count == 2


async def test_judge_failure_never_emits_unchecked_text():
    value = service()
    value.judge.side_effect = RuntimeError()
    result = await value.compute(request(), AsyncMock())
    assert result["model"] == "fixed" and not result["billable"]


async def test_tool_status_streams_and_draft_is_discarded_without_cancellation():
    from langgraph.config import get_stream_writer

    value = service()

    async def decide(context):
        get_stream_writer()({"event": "status", "data": {"text": "在翻帖子…"}})
        context["toolUsage"].append(dict(name="search_posts", success=True))
        context["toolResults"].append(dict(id="p1", name="search_posts", facts=["帖子介绍配色"]))
        return Directive(label="PASS")

    value.agent.run = decide
    events = [e async for e in value.stream(request())]
    assert [e["event"] for e in events] == ["status", "result"]
    assert value.speak.await_count == value.judge.await_count == 2
    assert "toolResults" not in value.speak.await_args_list[0].args[2]
    assert value.speak.await_args_list[1].args[2]["toolResults"][0]["id"] == "p1"
    assert not events[-1]["data"]["metrics"]["speculativeAdopted"]


async def test_image_skips_prejudge_and_is_rechecked_against_agent_note():
    value = service()
    value.agent.run.return_value = Directive(label="PASS", image_note="蓝色的天空")
    result = await value.compute(request(images=["data:image/jpeg;base64,YQ=="]), AsyncMock())
    assert value.speak.await_count == 2 and value.judge.await_count == 1
    assert value.judge.await_args.args[2]["image_note"] == "蓝色的天空"
    assert result["billable"]


async def test_second_unsafe_reply_falls_back_but_style_only_failure_is_recorded():
    value = service()
    value.judge.return_value = Judgment(safe=False, character=5, artificial=1)
    assert (await value.compute(request(), AsyncMock()))["model"] == "fixed"
    value.judge.return_value = Judgment(safe=True, character=8, artificial=8)
    result = await value.compute(request(), AsyncMock())
    assert result["billable"] and result["metrics"]["artificial"] == 8
