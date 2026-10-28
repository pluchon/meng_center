from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from pydantic import SecretStr

from app.config.settings import Settings
from evals.companion.budget import (
    EvaluationGateway,
    EvaluationLimit,
    EvaluationUnavailable,
    evaluation_settings,
)
from evals.companion.select import (
    SCENE,
    blind_context,
    history_before,
    resume_records,
)


def test_cache_acceptance_weights_tokens_and_keeps_cold_round_out_of_speech_target():
    from evals.companion.metrics import cache_summary, quality_failures

    def row(speech_input, speech_cached):
        return dict(result=dict(metrics=dict(calls=[
            dict(purpose="speech", input=speech_input, cached=speech_cached),
            dict(purpose="agent", input=1000, cached=600),
            dict(purpose="judge", input=1000, cached=500),
        ])))

    rows = [row(10000, 0), row(100, 100), row(900, 600)]
    cache = cache_summary(rows)
    assert cache["speech"]["ratio"] == 0.7
    assert cache["agent"]["ratio"] == 0.6
    assert cache["judge"]["ratio"] == 0.5
    failures = quality_failures(dict(flash=dict(
        firstBubbleP50Ms=2000, characters={}, cache=cache,
    )))
    assert failures == [dict(
        model="flash", metric="cacheHitRatio", purpose="judge", value=0.5, target=0.6,
    )]
    assert cache_summary([*rows[:1], *[row(100, 70) for _ in range(9)], row(90000, 0)])[
        "speech"
    ]["ratio"] == 0.7


def test_cache_without_input_is_unknown_and_cannot_pass_acceptance():
    from evals.companion.metrics import cache_summary, quality_failures

    cache = cache_summary([dict(result=dict(metrics=dict(calls=[])))])
    assert all(value["ratio"] is None for value in cache.values())
    failures = quality_failures(dict(flash=dict(
        firstBubbleP50Ms=2000, characters={}, cache=cache,
    )))
    assert {row["purpose"] for row in failures} == {"speech", "agent", "judge"}


@pytest.mark.parametrize("limit,ident", [(0, "id"), (10, None), (10, "../id")])
def test_evaluation_requires_explicit_authorization(limit, ident):
    with pytest.raises(ValueError):
        evaluation_settings(None, SimpleNamespace(token_budget=limit, budget_id=ident))


@pytest.mark.parametrize(
    "changes",
    [
        dict(token_budget=30001),
        dict(character=None),
        dict(compare_model="deepseek-v4-pro"),
        dict(acceptance=True),
        dict(primary_model="deepseek-v4-pro"),
        dict(small_sample=True),
        dict(phase_id=""),
    ],
)
def test_smoke_rejects_expansion_before_any_connection(changes):
    from evals.companion.budget import validate_mode

    values = dict(
        token_budget=30000,
        phase_id="one-phase",
        smoke=True,
        acceptance=False,
        compare_model=None,
        primary_model=None,
        character="NEKO",
        small_sample=False,
        restart_blind=False,
    )
    with pytest.raises(ValueError):
        validate_mode(SimpleNamespace(**(values | changes)))


def test_full_evaluation_requires_explicit_acceptance():
    from evals.companion.budget import validate_mode

    values = dict(phase_id="one-phase", smoke=False, acceptance=False, character=None, small_sample=False)
    with pytest.raises(ValueError, match="明确验收"):
        validate_mode(SimpleNamespace(**values))
    validate_mode(SimpleNamespace(**(values | dict(acceptance=True))))


@pytest.mark.parametrize("smoke,resume", [(False, True), (True, False), (False, False)])
def test_additional_budget_cannot_start_new_run_or_full_evaluation(smoke, resume):
    from evals.companion.budget import validate_mode

    with pytest.raises(ValueError, match="追加预算"):
        validate_mode(SimpleNamespace(continue_budget_stop=True, smoke=smoke, resume=resume))


async def test_usage_summary_separates_confirmed_tokens_from_unconfirmed_calls():
    redis = AsyncMock()
    redis.hgetall.return_value = {
        "speech:calls": "2",
        "speech:completed": "1",
        "speech:input_tokens": "100",
        "speech:output_tokens": "10",
        "agent:calls": "1",
        "agent:completed": "1",
        "agent:input_tokens": "20",
        "agent:output_tokens": "5",
    }
    redis.get.return_value = "900"
    gateway = EvaluationGateway(Settings.model_construct(api_key=SecretStr("fixture")), redis, "fixed", 1000)
    summary = await gateway.usage_summary()
    assert summary["calls"] == 3 and summary["confirmedTokens"] == 135
    assert summary["unconfirmedCalls"] == 1 and summary["chargedOrReservedTokens"] == 900


def test_blind_sample_is_fixed_and_selects_twenty_per_character():
    from evals.companion.cards import CARDS
    from evals.companion.select import blind_samples

    rows = [dict(id=f"{code}-{i}", card=code) for code in CARDS for i in range(35)]
    first = blind_samples(rows, 42)
    assert first == blind_samples(list(reversed(rows)), 42)
    assert first != blind_samples(rows, 43)
    assert len(first) == len({row["id"] for row in first}) == 20
    assert all(sum(row["card"] == code for row in first) == 20 for code in CARDS)


def test_fixed_clock_matches_time_sensitive_cases_even_when_question_sequence_repeats():
    from evals.companion.fixtures import scene

    assert "早上八点" in scene(0) and "星期六" in scene(0)
    assert "凌晨两点" in scene(20) and "晚上七点" in scene(10)
    assert "早上八点" in scene(30) and scene(30) != scene(0)


def test_acceptance_corpus_has_fixed_anonymous_sources_and_shared_clock():
    from evals.companion.corpus import load_corpus
    from evals.companion.fixtures import scene

    cases, metadata = load_corpus()
    assert len(cases) == len({row["id"] for row in cases}) == 35
    assert len(metadata["sha256"]) == 64
    assert {row["language"] for row in cases} == {"zh-Hant", "ja"}
    assert {row["category"] for row in cases} >= {"comfort", "daily", "question", "good_news", "rest"}
    assert all("http" not in row["text"] and "username" not in row for row in cases)
    assert "晚上七点" in scene(10, cases[10]["clock"])


async def test_recorded_tools_return_success_and_recall_only_provided_candidates():
    from test_companion import request

    from app.schemas.companion import Turn
    from evals.companion.fixtures import RecordedTools

    context = dict(request=request(), toolUsage=[], memoryIds=set())
    executor = RecordedTools()
    for name, args in [
        ("search_posts", dict(query="Vue")),
        ("search_posts", dict(query="配色")),
        ("read_post", dict(post_id=12)),
        ("my_growth", {}),
    ]:
        result = await executor.execute(dict(id=name, name=name, args=args), context)
        assert result["success"] and result["facts"]
    call = dict(id="r", name="recall", args=dict(query="城市"))
    assert not (await executor.execute(call, context))["facts"]
    context["request"].history = [Turn(role="USER", content="我住在台中，下周要考试。")]
    assert not (await executor.execute(call, context))["facts"]
    context["request"].history.append(Turn(role="USER", content="我搬到台北了。"))
    assert not (await executor.execute(call, context))["facts"] and not context["memoryIds"]
    context["request"].recalled = [{"id": 41, "content": "不来自已有前文的候选"}]
    recalled = await executor.execute(call, context)
    assert "不来自已有前文的候选" in str(recalled["facts"])
    assert recalled["speechFacts"] == ["不来自已有前文的候选"] and context["memoryIds"] == {41}
    assert all(row["success"] and row["recorded"] for row in context["toolUsage"])


async def test_recorded_recall_keeps_the_same_five_candidate_limit_as_runtime():
    from test_companion import request

    from evals.companion.fixtures import RecordedTools

    context = dict(
        request=request(recalled=[dict(id=i, content=f"候选{i}") for i in range(1, 8)]),
        toolUsage=[], memoryIds=set(),
    )
    result = await RecordedTools().execute(dict(id="r", name="recall", args=dict(query="候选")), context)
    assert len(result["facts"]) == 5 and context["memoryIds"] == {1, 2, 3, 4, 5}
    assert "候选6" not in str(result) and "候选7" not in str(result)


async def test_evaluation_trace_keeps_candidates_reviews_and_failures_without_extra_calls(monkeypatch):
    from test_companion import factory, request

    from app.runtime.safety.judge import Judgment
    from app.runtime.speech.parse import Parsed
    from app.services.companion import Companion
    from evals.companion.fixtures import RecordedCompanion

    candidate = Parsed(bubbles=["原始候选"])
    speak = AsyncMock(return_value=(candidate, {}))
    judge = AsyncMock(return_value=Judgment(safe=True, character=8, artificial=1))
    monkeypatch.setattr(Companion, "speak", speak)
    monkeypatch.setattr(Companion, "judge", judge)
    service = RecordedCompanion(SimpleNamespace(models=factory()), None)
    await service.speak(request(), [], {})
    await service.judge(request(), [], {}, candidate)
    candidate.bubbles[0] = "后续修改"
    assert service.generation_trace[0] == dict(phase="speech", bubbles=["原始候选"])
    assert service.generation_trace[1]["result"]["safe"] is True
    speak.assert_awaited_once()
    judge.assert_awaited_once()
    judge.side_effect = ValueError("fixture")
    with pytest.raises(ValueError):
        await service.judge(request(), [], {}, candidate)
    assert service.generation_trace[-1] == dict(phase="judge", errorType="ValueError")


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

    rows = [row("NEKO-0"), row("OTHER-0", "OTHER"), row("NEKO-1", result=False), row("NEKO-2"), row("NEKO-3")]
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


def test_blind_context_preserves_clock_and_the_single_voice_examples():
    normal = blind_context(dict(id="NEKO-28", card="NEKO"))
    safety = blind_context(dict(id="safety-NEKO-0", card="NEKO"))
    assert normal["mood"] == safety["mood"] == SCENE
    assert normal["examples"] == safety["examples"]
    assert normal["examples"]


def test_blind_protocol_upgrade_never_mixes_scores_or_changes_reply_conditions():
    prior_setup = dict(promptVersion="v2", candidates=["a", "b"])
    previous = dict(setup=prior_setup, budgetKey="fixed", records={"a": []}, pairs=[])
    updated = dict(**prior_setup, blindProtocolVersion=2)
    assert resume_records(previous, updated, "fixed")[0] == previous["records"]
    with pytest.raises(ValueError, match="重新评测"):
        resume_records({**previous, "pairs": [{"a": 7, "b": 8}]}, updated, "fixed")
    with pytest.raises(ValueError, match="重新评测"):
        resume_records(previous, {**updated, "promptVersion": "v3"}, "fixed")


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
        return dict(tics=[dict(text="喵", frequency=0.3)], kaomoji=["( ˘ω˘ )"])

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
