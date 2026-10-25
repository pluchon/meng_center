from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from pydantic import SecretStr, ValidationError

from app.config.settings import Settings
from evals.companion.budget import (
    EvaluationGateway,
    EvaluationLimit,
    EvaluationUnavailable,
    evaluation_settings,
)
from evals.companion.select import (
    SCENE,
    BlindScore,
    blind_context,
    history_before,
    judge_pair,
    resume_records,
)


@pytest.mark.parametrize("limit,ident", [(0, "id"), (10, None), (10, "../id")])
def test_evaluation_requires_explicit_authorization(limit, ident):
    with pytest.raises(ValueError):
        evaluation_settings(None, SimpleNamespace(token_budget=limit, budget_id=ident))


async def test_evaluation_limit_is_separate_from_business_gateway():
    redis = AsyncMock()
    redis.exists.return_value = False
    redis.eval.side_effect = [1, 0]
    gateway = EvaluationGateway(
        Settings.model_construct(api_key=SecretStr("fixture")), redis, "meng:ai:evaluation:fixed", 100
    )
    with pytest.raises(EvaluationLimit):
        await gateway.acquire("model", 101)
    assert redis.eval.await_args.args[-3:] == ("meng:ai:evaluation:fixed", 101, 100)
    assert gateway.exhausted


async def test_provider_permission_denial_stops_evaluation_instead_of_scoring_fallbacks():
    class Denied(Exception):
        status_code = 403

    gateway = EvaluationGateway(
        Settings.model_construct(api_key=SecretStr("fixture")), AsyncMock(), "fixed", 100
    )
    gateway.acquire = AsyncMock()
    gateway.failure = AsyncMock()
    call = AsyncMock(side_effect=Denied())
    with pytest.raises(Denied):
        await gateway.invoke("flash", "speech", 10, call, lambda _: {})
    assert call.await_count == 1
    assert gateway.infrastructure_error == dict(model="flash", purpose="speech", status=403)
    with pytest.raises(EvaluationUnavailable):
        gateway.check_available()


def test_blind_judge_history_stops_before_target_and_isolates_roles_and_safety():
    def row(ident, card="NEKO", result=True):
        return dict(id=ident, card=card, input=ident, result={"bubbles": [ident]} if result else None)

    rows = [row("NEKO-0"), row("GENKI-0", "GENKI"), row("NEKO-1", result=False), row("NEKO-2"), row("NEKO-3")]
    assert history_before(rows, rows[3]) == [
        dict(role="USER", content="NEKO-0"),
        dict(role="CHARACTER", content="NEKO-0"),
    ]
    assert history_before(rows, row("safety-NEKO-1-0")) == []


def test_resume_preserves_budget_and_identical_evaluation_conditions():
    setup = dict(promptVersion="v2", candidates=["a", "b"], smoke=False)
    previous = dict(setup=setup, budgetKey="fixed", records={"a": [{"id": "NEKO-0"}]}, pairs=[])
    assert resume_records(previous, setup, "fixed")[0] == previous["records"]
    with pytest.raises(ValueError, match="授权编号"):
        resume_records(previous, setup, "new")
    with pytest.raises(ValueError, match="重新评测"):
        resume_records(previous, {**setup, "promptVersion": "v3"}, "fixed")


def test_blind_context_preserves_time_stage_level_and_actual_examples():
    normal = blind_context(dict(id="NEKO-28", card="NEKO"))
    assert (normal["mood"], normal["stage"], normal["level"]) == (SCENE, 3, 2)
    safety = blind_context(dict(id="safety-NEKO-3-10", card="NEKO"))
    assert (safety["mood"], safety["stage"], safety["level"]) == ("", 5, 3)
    assert all(row["stage"] == 5 and row["level"] == 3 for row in safety["examples"])
    assert safety["examples"]


def test_blind_protocol_upgrade_never_mixes_scores_or_changes_reply_conditions():
    prior_setup = dict(promptVersion="v2", candidates=["a", "b"])
    previous = dict(setup=prior_setup, budgetKey="fixed", records={"a": []}, pairs=[])
    updated = dict(**prior_setup, blindProtocolVersion=2)
    assert resume_records(previous, updated, "fixed")[0] == previous["records"]
    with pytest.raises(ValueError, match="重新评测"):
        resume_records({**previous, "pairs": [{"a": 7, "b": 8}]}, updated, "fixed")
    with pytest.raises(ValueError, match="重新评测"):
        resume_records(previous, {**updated, "promptVersion": "v3"}, "fixed")


def test_blind_score_accepts_fractional_marks_without_relaxing_bounds():
    score = BlindScore(a=7.5, b=8, reason="语气更自然")
    assert (score.a, score.b) == (7.5, 8.0)
    for invalid in (-0.1, 10.1, float("nan"), True):
        with pytest.raises(ValidationError):
            BlindScore(a=invalid, b=8, reason="无效分数")


def test_restarting_blind_preserves_failed_replies_but_discards_all_prior_scores():
    setup = dict(promptVersion="v2", candidates=["a", "b"], blindProtocolVersion=2)
    rows = {"a": [dict(id="NEKO-0", passed=False)]}
    previous = dict(setup=setup, budgetKey="fixed", records=rows, pairs=[{"a": 7, "b": 8}])
    updated = {**setup, "blindProtocolVersion": 3}
    assert resume_records(previous, updated, "fixed", restart_blind=True) == (rows, [])
    assert previous["pairs"] == [{"a": 7, "b": 8}]
    with pytest.raises(ValueError, match="生成条件"):
        resume_records(previous, {**updated, "promptVersion": "v3"}, "fixed", restart_blind=True)
    with pytest.raises(ValueError, match="授权编号"):
        resume_records(previous, updated, "different", restart_blind=True)


@pytest.mark.parametrize("kind", ["missing", "malformed"])
async def test_blind_format_retry_keeps_first_valid_score_and_original_payload(kind):
    invalid = None
    if kind == "malformed":
        with pytest.raises(ValidationError) as captured:
            BlindScore(a=11, b=8, reason="范围错误")
        invalid = captured.value
    valid = BlindScore(a=7.5, b=8, reason="有效评分")
    service = SimpleNamespace(
        structured=AsyncMock(side_effect=[invalid, valid, BlindScore(a=10, b=10, reason="未使用")])
    )
    payload = {"input": "虚构问题", "responses": {"A": ["回复甲"], "B": ["回复乙"]}}
    assert await judge_pair(service, payload) is valid
    assert service.structured.await_count == 2
    assert all(call.args[2] is payload for call in service.structured.await_args_list)


async def test_blind_format_retry_is_bounded_and_never_accepts_missing_scores():
    service = SimpleNamespace(structured=AsyncMock(return_value=None))
    with pytest.raises(ValueError, match="保留检查点"):
        await judge_pair(service, {})
    assert service.structured.await_count == 2


def test_metrics_compare_legacy_and_new_reports_with_same_latency_and_style_denominators():
    from evals.companion.metrics import compare, summarize

    def row(index, content, latency, tool=False):
        return dict(
            id=f"NEKO-{index}",
            card="NEKO",
            passed=True,
            result=dict(
                bubbles=[content],
                billable=True,
                toolUsage=[dict(name="recall")] if tool else [],
                metrics=dict(firstBubbleMs=latency, calls=[dict(purpose="agent", output=40)]),
            ),
        )

    def cards(_):
        return dict(tics=[dict(text="喵", frequency=[0.15, 0.3, 0.45])], kaomoji=["( ˘ω˘ )"])

    before = summarize({"flash": [row(0, "嗯，歇会儿喵", 7000), row(1, "好呀", 9000)]}, cards)
    after = summarize(
        {"flash": [row(0, "嗯，( ˘ω˘ )", 3000), row(1, "好呀", 5000), row(2, "想起来了", 20000, True)]}, cards
    )
    difference = compare(after, before)["flash"]
    assert difference["before"]["firstBubbleP50Ms"] == 8000
    assert difference["after"]["firstBubbleP50Ms"] == 4000
    assert difference["firstBubbleP50DeltaMs"] == -4000
    assert after["flash"]["characters"]["NEKO"]["kaomojiRatio"] == pytest.approx(1 / 3)
    assert before["flash"]["characters"]["NEKO"]["tics"]["喵"]["frequency"] == 0.5
    mapped = compare({"deepseek-flash": after["flash"]}, {"deepseek-v4.1-flash-1017": before["flash"]})
    assert mapped["deepseek-flash"]["baselineModel"] == "deepseek-v4.1-flash-1017"
    assert mapped["deepseek-flash"]["firstBubbleP50DeltaMs"] == -4000


def test_background_probe_detects_three_consecutive_returns_and_missing_replies():
    from evals.companion.metrics import summarize_background

    rows = [
        dict(card="NEKO", result=dict(billable=True, bubbles=[reply]))
        for reply in ("今天在贴标签", "花盆还没有贴好", "窗台上的那张", "嗯，去吧", "这首歌怎么样")
    ]
    result = summarize_background({"flash": rows})["flash"]["characters"]["NEKO"]
    assert result["backgroundMentions"] == result["maxConsecutiveMentions"] == 3
    assert not result["passed"]
    rows[1]["result"] = None
    assert summarize_background({"flash": rows})["flash"]["characters"]["NEKO"]["missing"] == 1


def test_fast_fixed_fallbacks_do_not_reduce_model_reply_latency():
    from evals.companion.metrics import summarize

    def row(index, latency, billable):
        return dict(
            id=f"NEKO-{index}",
            card="NEKO",
            passed=billable,
            result=dict(billable=billable, bubbles=["嗯"], toolUsage=[], metrics=dict(firstBubbleMs=latency)),
        )

    result = summarize({"flash": [row(0, 3000, True), row(1, 5000, True), row(2, 1, False)]}, lambda _: {})[
        "flash"
    ]
    assert result["firstBubbleP50Ms"] == 4000 and result["noToolSamples"] == 2
    assert result["fallbackCases"] == 1 and result["passed"] == 2 and result["cases"] == 3
