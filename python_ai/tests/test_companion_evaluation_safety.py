import argparse
import copy
import json
from contextlib import asynccontextmanager
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from langchain_core.callbacks import UsageMetadataCallbackHandler
from langchain_core.runnables import RunnableLambda

from app.runtime.models.gateway import ModelUnavailable
from app.schemas.models import Moderation
from evals import run as station
from evals.companion import decisions
from evals.companion.metrics import baseline_metrics


async def test_decision_fixtures_expose_readable_post_ids_and_cover_every_business_tool():
    from app.runtime.tools.registry import TOOLS

    assert {case[3] for case in decisions.CASES if case[3]} == set(TOOLS)
    context = dict(observed=[], toolUsage=[], memoryIds=set())
    fixtures = decisions.Fixtures()
    search = await fixtures.execute(dict(id="s1", name="search_posts", args={"query": "新番"}), context)
    read = await fixtures.execute(
        dict(id="r1", name="read_post", args={"post_id": search["sources"][0]["postId"]}), context
    )
    assert search["success"] and read["success"] and "新番" in read["facts"][0]
    unknown = await fixtures.execute(dict(id="r2", name="read_post", args={"post_id": 999}), context)
    assert not unknown["success"]
    assert context["toolUsage"][-1] == dict(name="read_post", success=False)


def test_conversation_replay_uses_only_saved_successful_history_without_mutation():
    first = dict(id="NEKO-0", card="NEKO", input="昨天有些累", passed=True,
                 result=dict(bubbles=["歇一下。", "慢慢来。"]))
    failed = dict(id="NEKO-1", input="今天又睡过头了", card="NEKO", scene="上午九点",
                  passed=False, decisionIssues=["未请求工具"], result=dict(bubbles=["旧失败回复"]))
    report = dict(complete=False, records={"fixture": [first, failed]})
    original = copy.deepcopy(report)
    replay = decisions.conversation_context(report)
    assert replay["content"] == failed["input"] and replay["mood"] == failed["scene"]
    assert [turn.content for turn in replay["history"]] == [first["input"], "歇一下。\n慢慢来。"]
    assert report == original and "旧失败回复" not in str(replay)
    for mutation in (dict(complete=True), dict(records={}),
                     dict(records={"fixture": [{**first, "passed": False}, failed]})):
        with pytest.raises(ValueError):
            decisions.conversation_context({**report, **mutation})


def test_review_replay_keeps_actual_candidate_and_rejects_missing_evidence():
    row = dict(id="NEKO-0", card="NEKO", input="和流浪猫熟起来了", scene="下午",
               passed=False, result=dict(bubbles=["固定兜底"]),
               generationTrace=[dict(phase="speech", bubbles=["初稿"]),
                                dict(phase="judge"), dict(phase="speech", bubbles=["实际候选"])])
    report = dict(complete=False, records={"fixture": [row]})
    original = copy.deepcopy(report)
    replay = decisions.review_context(report)
    assert replay["candidate"] == ["实际候选"] and replay["history"] == []
    assert report == original
    for changed in (dict(toolUsage=[dict(name="recall")]), dict(sources=[dict(postId=1)]),
                    dict(mentionedMemoryId=1)):
        rejected = copy.deepcopy(report)
        rejected["records"]["fixture"][0]["result"].update(changed)
        with pytest.raises(ValueError, match="不重建"):
            decisions.review_context(rejected)
    row.pop("generationTrace")
    with pytest.raises(ValueError, match="实际候选"):
        decisions.review_context(report)


def test_agent_replay_can_diagnose_a_failed_round_without_a_complete_result():
    source = dict(complete=False, records={"deepseek-flash": [dict(
        id="NEKO-0", card="NEKO", input="早上好呀。", scene="早上八点", passed=False, result=None,
    )]})
    context = decisions.conversation_context(source)
    assert context["content"] == "早上好呀。" and context["history"] == []
    assert source["records"]["deepseek-flash"][0]["result"] is None


@pytest.mark.parametrize("rejected,expected_rejection", [(False, False), (True, True), (True, False)])
async def test_review_only_calls_judge_and_preserves_expected_rejection(
    monkeypatch, tmp_path, rejected, expected_rejection
):
    from pydantic import SecretStr

    from app.config.settings import Settings
    from app.runtime.safety.judge import Judgment

    settings = Settings.model_construct(redis_password=SecretStr("fixture"))
    redis = AsyncMock()
    gateway = SimpleNamespace(usage_summary=AsyncMock(return_value={"chargedOrReservedTokens": 0}))
    monkeypatch.setattr(decisions, "Settings", lambda: settings)
    monkeypatch.setattr(decisions, "evaluation_settings", lambda *args: (settings, "fixture-budget"))
    monkeypatch.setattr(decisions, "Redis", lambda **kwargs: redis)
    monkeypatch.setattr(decisions, "EvaluationGateway", lambda *args: gateway)
    monkeypatch.setattr(decisions, "claim_run", AsyncMock())
    monkeypatch.setattr(decisions, "ModelFactory", lambda *args: SimpleNamespace())
    judge = AsyncMock(return_value=Judgment(
        safe=True, character=8, artificial=1, unsupported=["无依据推断"] if rejected else [],
    ))
    service = SimpleNamespace(judge=judge, compute=AsyncMock(), generation_trace=[])
    monkeypatch.setattr(decisions, "RecordedCompanion", lambda *args: service)
    row = dict(id="NEKO-0", card="NEKO", input="原话", scene="下午", passed=False,
               result=dict(bubbles=["兜底"]), generationTrace=[dict(phase="speech", bubbles=["候选"])])
    source, output = tmp_path / "source.json", tmp_path / "review.json"
    source.write_text(json.dumps(dict(complete=False, records={"fixture": [row]})), encoding="utf-8")
    args = argparse.Namespace(
        token_budget=100, budget_id="fixture", phase_id="fixture-phase", case="conversation",
        judge_only=True, expect_review_rejection=expected_rejection,
        conversation_report=source, output=str(output),
    )
    expected = rejected == expected_rejection
    assert await decisions.run(args) == (0 if expected else 1)
    judge.assert_awaited_once()
    service.compute.assert_not_awaited()
    assert judge.await_args.args[0].content == "原话"
    assert judge.await_args.args[3].bubbles == ["候选"]
    saved = json.loads(output.read_text(encoding="utf-8"))
    assert saved["replayedConversation"]["mode"] == "judge"
    assert saved["cases"][0]["passed"] is expected


@pytest.mark.parametrize("case,rejected", [(None, False), ("weather", True), ("recall", True)])
async def test_decision_failure_checkpoints_usage_and_stops_before_next_call(
    monkeypatch, tmp_path, case, rejected
):
    redis = AsyncMock()
    gateway = SimpleNamespace(usage_summary=AsyncMock(return_value={"chargedOrReservedTokens": 0}))
    settings = SimpleNamespace(model_copy=lambda **kwargs: SimpleNamespace())
    monkeypatch.setattr(decisions, "Settings", lambda: settings)
    monkeypatch.setattr(decisions, "evaluation_settings", lambda *args: (settings, "fixture-budget"))
    # 只替换初始化需要的连接参数，所有客户端与模型都使用离线替身。
    settings.model_copy = lambda **kwargs: SimpleNamespace(
        redis_host="localhost", redis_port=1, redis_password=SimpleNamespace(get_secret_value=lambda: "")
    )
    monkeypatch.setattr(decisions, "Redis", lambda **kwargs: redis)
    monkeypatch.setattr(decisions, "EvaluationGateway", lambda *args: gateway)
    claim = AsyncMock()
    monkeypatch.setattr(decisions, "claim_run", claim)
    monkeypatch.setattr(decisions, "ModelFactory", lambda *args: SimpleNamespace())
    async def reply(context):
        from app.runtime.models.gateway import CURRENT

        CURRENT.get().calls.append(dict(purpose="agent", input=100, output=1, cached=80,
                                       cacheCreationTokens=20))
        if rejected:
            context["toolUsage"].append(dict(name="recall", success=False, rejected=True))
        return SimpleNamespace(label="PASS", model_dump=lambda **kwargs: {})

    agent = SimpleNamespace(run=AsyncMock(side_effect=reply))
    monkeypatch.setattr(decisions, "Agent", lambda *args: agent)
    output = tmp_path / "decisions.json"
    args = argparse.Namespace(
        token_budget=100, budget_id="fixture", phase_id="fixture-phase", output=str(output), case=case
    )

    assert await decisions.run(args) == 1
    assert agent.run.await_count == 1
    claim.assert_awaited_once()
    report = json.loads(output.read_text(encoding="utf-8"))
    assert not report["complete"] and len(report["cases"]) == 1
    assert report["usage"]["chargedOrReservedTokens"] == 0
    assert report["cases"][0]["passed"] is False
    assert bool(report["cases"][0]["toolUsage"]) == rejected
    assert report["cases"][0]["calls"] == [dict(
        purpose="agent", input=100, output=1, cached=80, cacheCreationTokens=20,
    )]
    redis.aclose.assert_awaited_once()


@pytest.mark.parametrize("unsupported,passed", [(0, True), (1, False)])
async def test_runtime_replay_uses_saved_history_and_the_full_acceptance_gate(
    monkeypatch, tmp_path, unsupported, passed
):
    from pydantic import SecretStr

    from app.config.settings import Settings

    settings = Settings.model_construct(redis_password=SecretStr("fixture"))
    redis = AsyncMock()
    gateway = SimpleNamespace(usage_summary=AsyncMock(return_value={"chargedOrReservedTokens": 0}))
    monkeypatch.setattr(decisions, "Settings", lambda: settings)
    monkeypatch.setattr(decisions, "evaluation_settings", lambda *args: (settings, "fixture-budget"))
    monkeypatch.setattr(decisions, "Redis", lambda **kwargs: redis)
    monkeypatch.setattr(decisions, "EvaluationGateway", lambda *args: gateway)
    monkeypatch.setattr(decisions, "claim_run", AsyncMock())
    monkeypatch.setattr(decisions, "ModelFactory", lambda *args: SimpleNamespace())
    result = dict(billable=True, model=settings.speech_model, bubbles=["软糖呀。"], toolUsage=[],
                  metrics=dict(unsupported=unsupported, artificial=1))
    service = SimpleNamespace(compute=AsyncMock(return_value=result), generation_trace=[dict(phase="speech")])
    monkeypatch.setattr(decisions, "RecordedCompanion", lambda *args: service)
    first = dict(id="NEKO-0", card="NEKO", input="昨天有点累", passed=True,
                 result=dict(bubbles=["歇一下。", "慢慢来。"]))
    failed = dict(id="NEKO-1", card="NEKO", input="这个软糖真好吃。", scene="下午三点",
                  passed=False, decisionIssues=[], result=dict(bubbles=["旧失败回复"]))
    source = tmp_path / "source.json"
    source.write_text(
        json.dumps(dict(complete=False, records={"fixture": [first, failed]})), encoding="utf-8"
    )
    output = tmp_path / "replay.json"
    args = argparse.Namespace(
        token_budget=100, budget_id="fixture", phase_id="fixture-phase", case="conversation",
        runtime=True, conversation_report=source, output=str(output),
    )
    assert await decisions.run(args) == (0 if passed else 1)
    body = service.compute.await_args.args[0]
    assert body.content == failed["input"] and body.mood == failed["scene"]
    assert body.turn_id == failed["id"]
    assert [turn.content for turn in body.history] == [first["input"], "歇一下。\n慢慢来。"]
    assert body.examples and not body.recalled
    saved = json.loads(output.read_text(encoding="utf-8"))
    assert saved["complete"] is passed and saved["cases"][0]["passed"] is passed
    assert saved["cases"][0]["result"]["generationTrace"] == service.generation_trace
    assert saved["replayedConversation"]["sourceRound"] == "NEKO-1"
    service.compute.assert_awaited_once()


def test_legacy_baseline_only_reuses_naitang_records_and_preserves_recorded_style():
    def record(code, latency):
        return {
            "id": code,
            "card": code,
            "passed": True,
            "result": {"bubbles": ["收到。"], "billable": True, "metrics": {"firstBubbleMs": latency}},
        }

    saved_style = {"tics": {"喵": {"frequency": 0.2, "target": 0.3}}}
    report = {
        "records": {"fixture-model": [record("NEKO", 100), record("RETIRED", 8000)]},
        "metrics": {"fixture-model": {"characters": {"NEKO": saved_style}}},
    }
    original = copy.deepcopy(report)

    def card(code):
        assert code == "NEKO"
        return {}

    result = baseline_metrics(report, card)["fixture-model"]
    assert result["cases"] == 1 and result["firstBubbleP50Ms"] == 100
    assert result["characters"]["NEKO"]["tics"] == saved_style["tics"]
    assert report == original


async def test_station_provider_failure_preserves_previous_result_and_stops(monkeypatch, tmp_path):
    settings = SimpleNamespace(
        redis_host="localhost",
        redis_port=1,
        redis_password=SimpleNamespace(get_secret_value=lambda: ""),
        structured_method="function_calling",
    )
    settings.model_copy = lambda **kwargs: settings
    redis = AsyncMock()
    gateway = SimpleNamespace(usage_summary=AsyncMock(return_value={"chargedOrReservedTokens": 0}))
    monkeypatch.setattr(station, "Settings", lambda: settings)
    monkeypatch.setattr(station, "evaluation_settings", lambda *args: (settings, "fixture-budget"))
    monkeypatch.setattr(station, "Redis", lambda **kwargs: redis)
    monkeypatch.setattr(station, "EvaluationGateway", lambda *args: gateway)
    claim = AsyncMock()
    monkeypatch.setattr(station, "claim_run", claim)

    @asynccontextmanager
    async def usage():
        yield UsageMetadataCallbackHandler()

    monkeypatch.setattr(station, "ModelFactory", lambda *args: SimpleNamespace(judge=None, usage=usage))
    calls = []

    async def invoke(payload):
        calls.append(payload)
        if len(calls) > 1:
            raise ModelUnavailable("offline")
        return Moderation(verdict="PASS")

    monkeypatch.setattr(
        station, "TaskRuntime", lambda *args: SimpleNamespace(chain=lambda name: RunnableLambda(invoke))
    )
    output = tmp_path / "station.json"
    args = argparse.Namespace(
        baseline=None, output=output, token_budget=100, budget_id="fixture", phase_id="fixture-phase"
    )
    with pytest.raises(ModelUnavailable):
        await station.evaluate(args)
    report = json.loads(output.read_text(encoding="utf-8"))
    assert len(calls) == 2 and len(report["records"]) == 1
    assert report["stopReason"] == "ModelUnavailable" and not report["complete"]
    assert report["usage"]["chargedOrReservedTokens"] == 0
    claim.assert_awaited_once()
    redis.aclose.assert_awaited_once()
