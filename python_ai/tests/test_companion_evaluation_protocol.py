import asyncio
import copy
import json
import logging
from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import pytest
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from pydantic import SecretStr, ValidationError

from app.config.settings import Settings
from app.runtime.context.cache import changes, fingerprints, public_hashes
from app.runtime.models.gateway import CURRENT, ModelGateway, RoundUsage
from app.runtime.speech.signals import imitation, imitation_summary
from app.runtime.speech.slop import signals as slop_signals
from evals.companion import cards
from evals.companion.blind import (
    DIMENSIONS,
    JUDGES,
    BlindJudge,
    Dimensions,
    PairScore,
    agreement,
    judge_both_orders,
    model_scores,
    score_order,
    spearman,
    validate_candidates,
)
from evals.companion.protocol import comparison_plan, comparison_status, generation_conditions
from evals.companion.slop_profile import candidates, frequency, profile


def scored(a, b):
    return PairScore(
        a=Dimensions(**dict.fromkeys(DIMENSIONS, a)),
        b=Dimensions(**dict.fromkeys(DIMENSIONS, b)),
        reason="只按质量判断",
    )


async def test_order_bias_becomes_tie_and_checkpoint_resumes_without_repeating_calls():
    # 同一裁判两次都偏好A，交换顺序后映射回模型应记平局。
    service = SimpleNamespace(structured=AsyncMock(side_effect=[scored(9, 7), scored(9, 7)]))
    names = ["mouth1", "mouth2"]
    rows = {name: dict(result=dict(bubbles=[name])) for name in names}
    history = {name: [dict(role="USER", content=name)] for name in names}
    progress, checkpoint = {}, AsyncMock()
    result = await judge_both_orders(service, {"input": "测试"}, names, rows, history, progress, checkpoint)
    for dimension in result["dimensions"].values():
        assert dimension == dict(scores=dict(mouth1=8, mouth2=8), preference=0, orderConsistent=False)
    calls = service.structured.await_args_list
    assert calls[0].args[2]["responses"]["A"] == ["mouth1"]
    assert calls[1].args[2]["responses"]["A"] == ["mouth2"]
    assert calls[1].args[2]["histories"]["A"] == history["mouth2"]
    assert "长短不影响" in calls[0].args[1]
    assert checkpoint.await_count == 4
    await judge_both_orders(service, {}, names, rows, history, progress, checkpoint)
    assert service.structured.await_count == 2


async def test_partial_judge_checkpoint_reuses_first_order_and_retries_only_bad_format():
    service = SimpleNamespace(structured=AsyncMock(side_effect=[None, scored(7, 9)]))
    first = dict(mouth1=dict.fromkeys(DIMENSIONS, 9), mouth2=dict.fromkeys(DIMENSIONS, 7))
    progress = dict(orders=[first])
    rows = {name: dict(result=dict(bubbles=[name])) for name in ("mouth1", "mouth2")}
    result = await judge_both_orders(
        service, {}, ["mouth1", "mouth2"], rows, dict(mouth1=[], mouth2=[]), progress
    )
    assert result["dimensions"]["natural"]["preference"] == 1
    assert service.structured.await_count == 2


async def test_failed_format_attempts_survive_resume_without_extra_calls():
    attempts, checkpoint = [], AsyncMock()
    service = SimpleNamespace(structured=AsyncMock(return_value=None))
    for _ in range(2):
        with pytest.raises(ValueError, match="保留检查点"):
            await score_order(service, {}, attempts, checkpoint)
    assert service.structured.await_count == 2
    assert [row["state"] for row in attempts] == ["invalid", "invalid"]


async def test_interrupted_attempt_is_not_refunded_on_resume():
    attempts = [dict(state="started")]
    service = SimpleNamespace(structured=AsyncMock(return_value=None))
    with pytest.raises(ValueError, match="保留检查点"):
        await score_order(service, {}, attempts)
    assert service.structured.await_count == 1
    assert len(attempts) == 2


async def test_blind_transport_accepts_only_schema_valid_json_and_records_raw_failure():
    from evals.companion.blind import JudgeResponseError

    runnable = SimpleNamespace(ainvoke=AsyncMock())
    model = SimpleNamespace(with_structured_output=MagicMock(return_value=runnable))
    judge = BlindJudge(
        SimpleNamespace(
            judge=model,
            semaphores={"judge": asyncio.Semaphore(1)},
            settings=SimpleNamespace(structured_method="function_calling"),
        )
    )
    runnable.ainvoke.return_value = dict(
        parsed=None, raw=AIMessage(content=scored(7, 8).model_dump_json()), parsing_error=None
    )
    assert (await judge.structured(PairScore, "提示", {})).b.natural == 8
    runnable.ainvoke.return_value["raw"] = AIMessage(
        content="无法评分", response_metadata={"finish_reason": "stop"}
    )
    with pytest.raises(JudgeResponseError) as failed:
        await judge.structured(PairScore, "提示", {})
    assert failed.value.diagnostic["content"] == "无法评分"
    assert failed.value.diagnostic["finishReason"] == "stop"
    runnable.ainvoke.return_value["raw"] = AIMessage(
        content="",
        invalid_tool_calls=[dict(name="PairScore", args='{"a": character: 8}', id="fixture", error="JSON")],
    )
    with pytest.raises(JudgeResponseError) as malformed:
        await judge.structured(PairScore, "提示", {})
    assert malformed.value.diagnostic["invalidToolCalls"][0]["args"] == '{"a": character: 8}'


def test_dimension_scores_are_finite_bounded_numbers():
    assert scored(7.5, 8).a.natural == 7.5
    for value in (-1, 11, True, float("nan")):
        with pytest.raises(ValidationError):
            scored(value, 8)
    for models in (["same", "same"], ["mouth", "qwen3.8-flash"]):
        with pytest.raises(ValueError):
            validate_candidates(models)


def test_spearman_handles_ties_constants_and_low_agreement_is_not_a_gate():
    assert spearman([1, 1, 3, 4], [2, 2, 6, 8]) == pytest.approx(1)
    assert spearman([1, 2, 3], [3, 2, 1]) == pytest.approx(-1)
    assert spearman([1, 1, 1], [2, 3, 4]) is None
    assert spearman([1, 2], [1, 2]) is None
    pairs = []
    for number in range(4):
        judges = {}
        for index, judge in enumerate(JUDGES):
            judges[judge] = dict(
                dimensions={
                    dimension: dict(
                        scores={
                            "a": number if dimension == "natural" or index == 0 else 9 - number,
                            "b": number + 1 if dimension == "natural" or index == 0 else 8 - number,
                        }
                    )
                    for dimension in DIMENSIONS
                }
            )
        pairs.append(dict(judges=judges))
    result = agreement(pairs)
    assert result["natural"]["usableAsGate"] and result["natural"]["samples"] == 8
    assert not result["safety"]["usableAsGate"]
    assert model_scores(pairs, result) == dict(a=1.5, b=2.5)


def test_chinese_frequencies_do_not_cross_punctuation_or_utterances():
    counts = frequency(["你好，世界", "世", "界"])
    assert counts[2] == {"你好": 1, "世界": 1}
    assert "好世" not in counts[2]
    rows = candidates(["普通朋友聊天"] * 100, ["共同努力共同努力"] * 5)
    assert rows and all(row["count"] >= 5 and row["ratio"] > 20 for row in rows)
    assert candidates(["共同努力"] * 10, ["共同努力"] * 10) == []
    assert candidates(["普通聊天"] * 100, ["共同努力"] * 4) == []


def test_japanese_reference_cannot_be_used_as_chinese_baseline(tmp_path):
    path = tmp_path / "source.json"
    path.write_text(
        json.dumps(dict(language="ja", sources=["公开资料"], texts=["こんにちは"])), encoding="utf-8"
    )
    with pytest.raises(ValueError, match="中文口语"):
        profile(path, tmp_path / "does-not-exist")


def test_unreviewed_candidates_and_stale_profiles_never_become_slop_signals(monkeypatch):
    monkeypatch.setattr(
        "app.runtime.speech.slop.load",
        lambda _: dict(
            promptVersion="v1",
            catalogVersion="c1",
            candidates=[dict(phrase="台北")],
            reviewed=[
                dict(phrase="共同努力", isSlop=True, reason="重复泛化鼓励"),
                dict(phrase="台北", isSlop=False, reason="问题主题"),
            ],
        ),
    )
    assert slop_signals("model", "台北共同努力", "v1", "c1")["matches"] == [dict(phrase="共同努力", count=1)]
    assert slop_signals("model", "共同努力", "v2", "c1") == dict(profileApplicable=False, matches=[])


def test_self_imitation_window_excludes_old_matches_and_reports_both_halves():
    assert imitation("今天去公园散步", ["今天去公园散步"])["trigramOverlap"] == 1
    assert imitation("今天去公园散步", ["今天去公园散步"] + ["完全无关"] * 20)["trigramOverlap"] == 0
    assert not imitation("嗯", ["嗯"])["repeatedOpening"]
    summary = imitation_summary(["早上出门散步", "晚上准备做饭", "早上出门散步", "早上出门散步"])
    assert summary["secondHalf"]["meanTrigramOverlap"] > summary["firstHalf"]["meanTrigramOverlap"]
    assert summary["secondHalf"]["repeatedOpenings"] == 2


def message_segments(stable="角色", slow="快照", history=None):
    return fingerprints(
        [SystemMessage(stable), SystemMessage(slow), *(history or []), HumanMessage("本轮不断变化")]
    )


def test_segment_hashes_distinguish_append_only_from_replaced_history():
    previous = public_hashes(message_segments(history=[HumanMessage("第一条")]))
    current = message_segments(history=[HumanMessage("第一条"), AIMessage("回答")])
    assert changes(previous, current) == dict(changedSegments=["history"], historyAppendOnly=True)
    assert "第一条" not in json.dumps(current, ensure_ascii=False)
    mutated = message_segments(stable="角色变更", history=[HumanMessage("其他内容")])
    assert changes(previous, mutated) == dict(changedSegments=["stable", "history"], historyAppendOnly=False)
    assert changes(previous, message_segments(slow="快照变化", history=[HumanMessage("第一条")]))[
        "changedSegments"
    ] == ["slow"]


async def test_hot_cache_alert_is_session_scoped_and_never_contains_dialogue(caplog):
    redis = AsyncMock()
    redis.get.return_value = None
    gateway = ModelGateway(Settings.model_construct(api_key=SecretStr("fixture")), redis)
    current = RoundUsage(user=1, character="NEKO", turn="first", session=10)
    first = message_segments(stable="秘密台词")
    await gateway.cache_diagnostic("mouth", first, 100, 0, current)
    first_key = redis.get.await_args.args[0]
    redis.get.return_value = redis.set.await_args.args[1]
    current.turn = "second"
    logger = logging.getLogger("app.runtime.models.gateway")
    logger.addHandler(caplog.handler)
    try:
        await gateway.cache_diagnostic("mouth", message_segments(stable="另一段秘密"), 100, 20, current)
    finally:
        logger.removeHandler(caplog.handler)
    assert "speech_cache_break" in caplog.text and "stable" in caplog.text
    assert "秘密" not in caplog.text
    current.session = 11
    await gateway.cache_diagnostic("mouth", first, 100, 100, current)
    assert redis.get.await_args.args[0] != first_key


async def test_cache_observation_failure_does_not_retry_a_successful_model_call():
    redis = AsyncMock()
    redis.get.side_effect = RuntimeError("不可用")
    pipeline = MagicMock()
    pipeline.__aenter__ = AsyncMock(return_value=pipeline)
    pipeline.execute = AsyncMock()
    redis.pipeline = MagicMock(return_value=pipeline)
    gateway = ModelGateway(Settings.model_construct(api_key=SecretStr("fixture")), redis)
    token = CURRENT.set(RoundUsage(user=1, character="NEKO", turn="1", session=1))
    try:
        await gateway.record(
            "model", "speech", dict(input_tokens=100, output_tokens=5, _cache_segments=message_segments()), 1
        )
        assert CURRENT.get().calls[0]["input"] == 100
    finally:
        CURRENT.reset(token)


async def test_cache_observation_timeout_does_not_block_completed_reply():
    gateway = ModelGateway(Settings.model_construct(api_key=SecretStr("fixture")), AsyncMock())
    gateway.cache_diagnostic = AsyncMock(side_effect=lambda *args: None)

    async def unavailable(*args):
        await asyncio.Event().wait()

    gateway.cache_diagnostic.side_effect = unavailable
    await asyncio.wait_for(
        gateway.record(
            "model", "speech", dict(input_tokens=100, output_tokens=1, _cache_segments=message_segments()), 1
        ),
        timeout=0.8,
    )


def test_comparison_requires_external_complete_baseline_and_one_variable(tmp_path):
    output, baseline = tmp_path / "new.json", tmp_path / "old.json"
    baseline.write_text(json.dumps(dict(complete=True, setup={})), encoding="utf-8")
    for prior, variables in (
        (str(baseline), []),
        (None, ["角色卡说话方式"]),
        (str(baseline), ["角色卡说话方式", "模型"]),
    ):
        with pytest.raises(ValueError):
            comparison_plan(prior, variables, output)
    previous, plan = comparison_plan(str(baseline), ["角色卡说话方式"], output)
    setup = dict(promptVersion="v1", generationConditions={"models": "fixed"})
    assert not comparison_status(plan, setup, previous)["canConclude"]
    previous["setup"] = setup
    assert comparison_status(plan, setup, previous)["canConclude"]
    assert not comparison_status(plan, {**setup, "promptVersion": "v2"}, previous)["canConclude"]
    assert not comparison_status(plan, {**setup, "blindProtocolVersion": 5}, previous)["canConclude"]
    baseline.write_text(json.dumps(dict(complete=False)), encoding="utf-8")
    with pytest.raises(ValueError, match="完整报告"):
        comparison_plan(str(baseline), ["角色卡说话方式"], output)


def test_generation_fingerprint_allows_only_voice_tics_kaomoji_and_character_lines(monkeypatch):
    settings = Settings.model_construct(api_key=SecretStr("fixture"))
    before = generation_conditions(settings)
    assert json.loads(json.dumps(before)) == before
    another_provider = settings.model_copy(update={"base_url": "https://other.example/v1"})
    assert generation_conditions(another_provider) != before
    revised = copy.deepcopy(cards.CATALOG)
    revised["version"] = "new"
    revised["characters"][0]["card"]["voice"] = "新的说话节奏"
    revised["lines"][0]["dialogue"]["character"] = "新的示例台词"
    monkeypatch.setattr(cards, "CATALOG", revised)
    assert generation_conditions(settings) == before
    revised["characters"][0]["card"]["form"]["question"] = 0.7
    assert generation_conditions(settings) != before


@pytest.mark.parametrize(
    "mode", ["smoke", "single", "comparison", "failed", "interrupted", "metrics", "budget", "unexpected"]
)
async def test_selection_end_to_end_enforces_scale_and_judging_without_external_calls(
    monkeypatch, tmp_path, mode
):
    from evals.companion import select

    settings = Settings.model_construct(api_key=SecretStr("fixture"), redis_password=SecretStr("fixture"))
    monkeypatch.setattr(select, "Settings", lambda: settings)
    redis = AsyncMock()
    redis.eval.return_value = 1
    redis.hgetall.return_value = {}
    redis.get.return_value = "0"
    monkeypatch.setattr(select, "Redis", lambda **_: redis)
    monkeypatch.setattr(
        select, "ModelFactory", lambda configured, *args: SimpleNamespace(settings=configured)
    )
    monkeypatch.setattr(select, "CoverImageClient", lambda *args: None)
    monkeypatch.setattr(select, "Assistant", lambda models, _: SimpleNamespace(models=models))
    calls = []
    generated = []

    class Service:
        def __init__(self, assistant, _):
            self.models = assistant.models

        async def stream(self, request):
            generated.append(request)
            if mode == "interrupted":
                raise ConnectionError("模拟请求中断")
            yield dict(
                event="result",
                data=dict(
                    billable=mode != "failed",
                    model=self.models.settings.speech_model,
                    bubbles=[request.character, request.card["kaomoji"][0]],
                    toolUsage=[dict(name="search_posts")] if mode == "unexpected" else [],
                    metrics=dict(
                        unsupported=0, artificial=1, firstBubbleMs=4000 if mode == "metrics" else 100
                    ),
                ),
            )

        async def structured(self, schema, prompt, payload):
            calls.append(self.models.settings.judge_model)
            # 两个裁判返回相同的恒定分数；没有方差时不得把一致度当作验收门槛。
            base = list(cards.CARDS).index(payload.get("response", payload.get("responses", {}).get("A"))[0])
            if "response" in payload:
                from evals.companion.blind import SingleScore

                return SingleScore(
                    score=Dimensions(**dict.fromkeys(DIMENSIONS, 7 + base)), reason="按质量评分"
                )
            return scored(7 + base, 7 + base)

    monkeypatch.setattr(select, "Companion", Service)
    monkeypatch.setattr(select, "BlindJudge", lambda models: Service(SimpleNamespace(models=models), None))
    output = tmp_path / "result.json"
    arguments = [
        "--token-budget",
        "1000",
        "--budget-id",
        "offline",
        "--phase-id",
        "offline",
        "--output",
        str(output),
    ]
    arguments += (
        ["--smoke", "--character", "NEKO"]
        if mode in {"smoke", "failed", "interrupted", "budget"}
        else ["--acceptance"]
    )
    if mode == "comparison":
        arguments += ["--compare-model", "deepseek-v4-pro"]
    args = select.parser().parse_args(arguments)
    if mode in {"failed", "interrupted", "unexpected"}:
        with pytest.raises((ConnectionError, select.EvaluationUnavailable)):
            await select.run(args)
        report = json.loads(output.read_text(encoding="utf-8"))
        assert not report["complete"] and len(generated) == 1 and not calls
        assert bool(report["pendingTurn"]) == (mode == "interrupted")
        args.resume = True
        with pytest.raises((ValueError, select.EvaluationUnavailable)):
            await select.run(args)
        assert len(generated) == 1
        return
    if mode == "metrics":
        assert await select.run(args) == 1
        report = json.loads(output.read_text(encoding="utf-8"))
        assert report["stoppedReason"] == "MetricsNotMet" and not report["complete"]
        assert not calls and len(generated) == 35 and report["qualityFailures"]
        return
    await select.run(args)
    report = json.loads(output.read_text(encoding="utf-8"))
    if mode == "budget":
        first = copy.deepcopy(report["records"]["deepseek-flash"][0])
        unfinished = {**report["records"]["deepseek-flash"][1], "result": None, "passed": False}
        report.update(complete=False, stoppedReason="EvaluationLimit")
        report["records"]["deepseek-flash"] = [first, unfinished]
        output.write_text(json.dumps(report), encoding="utf-8")
        args.resume, args.continue_budget_stop, args.token_budget = True, True, 3000
        redis.get.return_value = "500"
        assert await select.run(args) == 0
        resumed = json.loads(output.read_text(encoding="utf-8"))
        rows = resumed["records"]["deepseek-flash"]
        assert resumed["complete"] and len(rows) == len({row["id"] for row in rows}) == 5
        assert rows[0] == first and rows[1]["attempts"] == [unfinished]
        assert len(generated) == 9 and sum(item.turn_id == "NEKO-0" for item in generated) == 1
        assert resumed["usage"]["tokenLimit"] == 3500
        assert resumed["usage"]["invocationTokenLimit"] == 3000
        assert resumed["continuations"][0]["retryId"] == "NEKO-1"
        return
    assert report["complete"] and len(report["pairs"]) == (0 if mode == "smoke" else 20)
    assert len(generated) == {"smoke": 5, "single": 35, "comparison": 70}[mode]
    assert len(calls) == {"smoke": 0, "single": 40, "comparison": 80}[mode]
    assert set(calls) == (set() if mode == "smoke" else set(JUDGES))
    if mode == "smoke":
        assert report["metrics"] == {} and report["setup"]["candidates"] == ["deepseek-flash"]
    else:
        assert all(
            row["spearman"] is None and not row["usableAsGate"] for row in report["judgeAgreement"].values()
        )
    assert next(iter(report)) == "usage"
    assert report["comparison"] == {} and not report["comparisonProtocol"]["canConclude"]
    assert report["winner"] is None
    assert report["catalog"]["version"] == cards.CATALOG["version"]
    args.resume = True
    with pytest.raises(ValueError, match="已经完成"):
        await select.run(args)
