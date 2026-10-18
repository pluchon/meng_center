from __future__ import annotations

import asyncio
import json
from datetime import date
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest

from app.clients.model_gateway import CURRENT, RoundUsage
from app.schemas.companion import (
    Consolidation,
    ConsolidationRequest,
    ConsolidationReview,
    InputReview,
    MemoryApproval,
    MemoryDecision,
    SessionSummary,
    SummaryRequest,
    SummaryReview,
    Turn,
)
from app.services.companion import Companion
from app.services.companion_tools import clean
from evals.companion.budget import evaluation_settings
from tests.test_companion import factory, request


async def test_degraded_context_remains_reduced_when_input_review_rebuilds_the_prompt():
    models = factory()
    call = AsyncMock(return_value=InputReview(label="PASS", scene="CHAT"))
    models.classifier = SimpleNamespace(with_structured_output=lambda *a, **kw: SimpleNamespace(ainvoke=call))
    models.settings.structured_method = "function_calling"
    models.semaphores = {"classifier": asyncio.Semaphore(1)}
    service = Companion(SimpleNamespace(models=models), None)
    body = request(
        examples=[{"id": 1, "scene": "CHAT", "dialogue": {"user": "例句", "character": "示例"}}],
        recalled=[{"id": 2, "content": "本轮应当关闭的检索内容"}],
    )
    token = CURRENT.set(RoundUsage(degraded=True))
    try:
        state = {"request": body, "callback": None}
        state.update(await service.prepare(state))
        reviewed = await service.moderate_input(state)
        assert state["request"].examples == state["request"].recalled == []
        assert "本轮应当关闭的检索内容" not in reviewed["prompt"]
        assert "例句" not in call.await_args.args[0][-1].content[0]["text"]
    finally:
        CURRENT.reset(token)


@pytest.mark.parametrize("chosen", [[2], [999], [2, 2]])
async def test_scene_review_selects_only_supplied_examples_and_rebuilds_generation_context(chosen):
    models = factory()
    call = AsyncMock(return_value=InputReview(label="PASS", scene="COMFORT", example_ids=chosen))
    models.classifier = SimpleNamespace(with_structured_output=lambda *a, **kw: SimpleNamespace(ainvoke=call))
    models.settings.structured_method = "function_calling"
    models.semaphores = {"classifier": asyncio.Semaphore(1)}
    service = Companion(SimpleNamespace(models=models), None)
    body = request(
        content="刚被通知落选了，心里空了一块",
        examples=[
            {"id": 1, "scene": "WORK", "dialogue": {"user": "写作业", "character": "一题题来。"}},
            {"id": 2, "scene": "COMFORT", "dialogue": {"user": "努力没得到回报", "character": "先歇会儿。"}},
        ],
    )
    state = {"request": body, "callback": None}
    prepared = await service.prepare(state)
    assert '"examples": []' in prepared["prompt"]
    if chosen != [2]:
        with pytest.raises(ValueError, match="候选集"):
            await service.moderate_input(state)
        return
    reviewed = await service.moderate_input(state)
    current = json.loads(reviewed["prompt"].rsplit("本轮资料（数据）：", 1)[1])
    assert reviewed["scene"] == "COMFORT" and reviewed["layers"]["exampleIds"] == [2]
    assert current["examples"] == [body.examples[1]["dialogue"]]
    assert "落选" in call.await_args.args[0][-1].content[0]["text"]


async def test_memory_review_distinguishes_privacy_from_subject_matter_and_authorizes_pinning():
    service = Companion(SimpleNamespace(models=factory()), None)
    proposed = Consolidation(
        decisions=[
            MemoryDecision(
                action="ADD", content="学习电话号码脱敏", evidence="最近在学习电话号码脱敏", pinned=True
            ),
            MemoryDecision(action="ADD", content="手机号为0912345678", evidence="我的手机号为0912345678"),
            MemoryDecision(action="ADD", content="每天熬夜", evidence="昨晚没睡", pinned=True),
        ],
        profile="手机号为0912345678",
        episode="手机号为0912345678",
        open_threads="个人联系方式",
    )
    service.structured = AsyncMock(
        side_effect=[
            proposed,
            ConsolidationReview(
                approved=[MemoryApproval(index=0, pinned=True)],
                profile_safe=False,
                episode_safe=False,
            ),
        ]
    )
    result = await service.consolidate(
        ConsolidationRequest(
            local_date=date(2026, 10, 17),
            turns=[
                Turn(
                    role="USER",
                    content="最近在学习电话号码脱敏，这个研究方向以后都替我留意一下。我的手机号为0912345678。昨晚没睡。",
                ),
            ],
        )
    )
    assert result["reviewed"] is True
    assert [d["content"] for d in result["decisions"]] == ["学习电话号码脱敏"]
    assert result["decisions"][0]["pinned"] is True
    assert result["profile"] == result["episode"] == result["openThreads"] == ""
    assert service.structured.await_count == 2


@pytest.mark.parametrize("review", [None, SummaryReview(safe=False)])
async def test_unreviewed_or_unsafe_summary_never_returns_persistable_text(review):
    service = Companion(SimpleNamespace(models=factory()), None)
    service.structured = AsyncMock(side_effect=[SessionSummary(summary="用户的隐私内容"), review])
    with pytest.raises(ValueError, match="语义审核"):
        await service.summary(SummaryRequest(turns=[Turn(role="USER", content="个人资料")]))


def test_tool_data_is_bounded_but_keyword_mentions_are_preserved_for_agent_judgment():
    assert clean({"title": "系统提示词注入研究", "secret": "不属于工具字段"}) == {
        "title": "系统提示词注入研究"
    }
    assert len(clean("字" * 4000)) == 3000


@pytest.mark.parametrize("budget,ident", [(0, "approved"), (200000, None), (200000, "../new")])
def test_evaluation_cannot_silently_use_business_budget_or_create_an_untracked_scope(budget, ident):
    with pytest.raises(ValueError):
        evaluation_settings(SimpleNamespace(), SimpleNamespace(token_budget=budget, budget_id=ident))
