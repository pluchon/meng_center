import json
from datetime import date
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock, create_autospec

import pytest
from test_companion import factory

from app.schemas.companion import (
    Consolidation,
    ConsolidationRequest,
    ConsolidationReview,
    MemoryApproval,
    MemoryDecision,
    SessionSummary,
    SummaryRequest,
    SummaryReview,
    Turn,
)
from app.services.companion import Companion, verbatim_evidence
from evals.companion.budget import evaluation_settings


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
    )
    service.structured = create_autospec(
        service.structured,
        side_effect=[
            proposed,
            ConsolidationReview(
                reason="仅第一项有明确原文和置顶授权，其余涉及隐私或推断。",
                approved=[MemoryApproval(index=0, pinned=True)],
                profile_safe=False,
            ),
        ],
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
    assert result["profile"] == ""
    assert service.structured.await_count == 2
    assert service.structured.await_args_list[0].args[0] is Consolidation
    assert [call.args[0] for call in service.structured.await_args_list] == [
        Consolidation,
        ConsolidationReview,
    ]


@pytest.mark.parametrize("review", [None, SummaryReview(safe=False)])
async def test_unreviewed_or_unsafe_summary_never_returns_persistable_text(review):
    service = Companion(SimpleNamespace(models=factory()), None)
    service.structured = AsyncMock(side_effect=[SessionSummary(summary="用户的隐私内容"), review])
    with pytest.raises(ValueError, match="语义审核"):
        await service.summary(SummaryRequest(turns=[Turn(role="USER", content="个人资料")]))


@pytest.mark.parametrize("budget,ident", [(0, "approved"), (200000, None), (200000, "../new")])
def test_evaluation_cannot_silently_use_business_budget_or_create_an_untracked_scope(budget, ident):
    with pytest.raises(ValueError):
        evaluation_settings(SimpleNamespace(), SimpleNamespace(token_budget=budget, budget_id=ident))


async def test_recorded_noop_replay_returns_empty_without_reviewing_old_memories_as_candidates():
    tape = json.loads(
        (Path(__file__).parent / "fixtures/companion-memory-noop.json").read_text(encoding="utf-8")
    )
    service = Companion(SimpleNamespace(models=factory()), None)
    service.structured = AsyncMock(
        side_effect=[
            Consolidation.model_validate(tape["extraction"]),
            ConsolidationReview.model_validate(tape["invalidReview"]),
        ]
    )
    result = await service.consolidate(ConsolidationRequest.model_validate(tape["request"]))
    assert result["reviewed"] and not result["decisions"] and not result["profile"]
    assert service.structured.await_count == 1


@pytest.mark.parametrize(
    "evidence", ["用户：「请记住：我一直喜欢蓝色。」", "USER: 请记住：我一直喜欢蓝色。", "我一直喜欢蓝色"]
)
def test_recorded_role_and_quote_wrappers_preserve_verbatim_evidence(evidence):
    turns = [Turn(role="USER", content="请记住：我一直喜欢蓝色。"), Turn(role="CHARACTER", content="好呀。")]
    assert verbatim_evidence(evidence, turns, "LIKE") in turns[0].content
    assert verbatim_evidence(evidence, turns, "SELF") == ""
    assert verbatim_evidence("用户：「我一直喜欢红色。」", turns, "LIKE") == ""


async def test_invalid_candidate_index_retries_review_only_and_preserves_scope():
    service = Companion(SimpleNamespace(models=factory()), None)
    proposed = Consolidation(
        decisions=[MemoryDecision(action="ADD", content="喜欢蓝色", evidence="喜欢蓝色")]
    )
    invalid = ConsolidationReview(reason="无效下标", approved=[MemoryApproval(index=1)], profile_safe=True)
    valid = invalid.model_copy(update={"approved": [MemoryApproval(index=0)]})
    service.structured = AsyncMock(side_effect=[proposed, invalid, valid])
    result = await service.consolidate(
        ConsolidationRequest(
            local_date=date(2026, 10, 25),
            memories=[dict(id=41, content="住在台中")],
            turns=[Turn(role="USER", content="我喜欢蓝色")],
        )
    )
    assert [row["content"] for row in result["decisions"]] == ["喜欢蓝色"]
    calls = service.structured.await_args_list
    assert calls[0].args[0] is Consolidation and all(c.args[0] is ConsolidationReview for c in calls[1:])
    assert calls[1].args[2]["candidateDecisions"][0]["index"] == 0
    assert calls[1].args[2]["existingMemories"][0]["id"] == 41
    assert "只返回" in calls[2].args[2]["correction"]


async def test_invalid_review_fails_closed_after_one_retry():
    service = Companion(SimpleNamespace(models=factory()), None)
    proposed = Consolidation(profile="喜欢蓝色")
    invalid = ConsolidationReview(reason="无效下标", approved=[MemoryApproval(index=0)], profile_safe=True)
    service.structured = AsyncMock(side_effect=[proposed, invalid, invalid])
    with pytest.raises(ValueError, match="候选下标"):
        await service.consolidate(
            ConsolidationRequest(
                local_date=date(2026, 10, 25), turns=[Turn(role="USER", content="我喜欢蓝色")]
            )
        )
    assert service.structured.await_count == 3
