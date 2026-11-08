from types import SimpleNamespace

import pytest
from test_companion import request

from app.graphs.naitang import build
from app.runtime.agent.agent import Context
from app.runtime.agent.directive import Directive


def service(label, calls):
    async def run(ctx):
        calls.append("agent")
        return Directive(label=label)

    async def speak(*args, **kwargs):
        calls.append("speak")
        return SimpleNamespace(bubbles=["好呀"], sticker=None), {}

    async def judge(*args, **kwargs):
        calls.append("judge")
        return SimpleNamespace(
            safe=True, unsupported=[], artificial=0, character=0, reason="", mentioned_memory=None
        )

    return SimpleNamespace(
        agent=SimpleNamespace(run=run),
        speak=speak,
        judge=judge,
        prompt_version=lambda req: "fixture",
        models=SimpleNamespace(settings=SimpleNamespace(speech_model="fixture-speech")),
    )


async def run_turn(label, allowed):
    calls = []
    ctx = dict(
        request=request(message_allowed=allowed),
        service=service(label, calls),
        emotions=[],
        memoryIds=set(),
        sources=[],
        toolUsage=[],
    )
    state = await build().ainvoke({}, context=Context(ctx))
    return state["result"], calls


@pytest.mark.parametrize("label", ["PASS", "ROMANCE_PUSH"])
async def test_exhausted_quota_only_runs_agent_and_stays_billable(label):
    result, calls = await run_turn(label, allowed=False)
    assert calls == ["agent"]
    assert result["billable"] is True


async def test_exhausted_quota_still_answers_crisis_without_billing():
    result, calls = await run_turn("CRISIS", allowed=False)
    assert calls == ["agent"]
    assert result["billable"] is False
    assert result["bubbles"] == ["联系现实里信任的人。"]


async def test_normal_quota_keeps_parallel_draft_and_review():
    result, calls = await run_turn("PASS", allowed=True)
    assert {"agent", "speak", "judge"} <= set(calls)
    assert result["billable"] is True and result["bubbles"] == ["好呀"]
