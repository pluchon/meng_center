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


async def test_decision_failure_checkpoints_usage_and_stops_before_next_call(monkeypatch, tmp_path):
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
    agent = SimpleNamespace(
        run=AsyncMock(return_value=SimpleNamespace(label="PASS", model_dump=lambda **kwargs: {}))
    )
    monkeypatch.setattr(decisions, "Agent", lambda *args: agent)
    output = tmp_path / "decisions.json"
    args = argparse.Namespace(
        token_budget=100, budget_id="fixture", phase_id="fixture-phase", output=str(output), case=None
    )

    assert await decisions.run(args) == 1
    assert agent.run.await_count == 1
    claim.assert_awaited_once()
    report = json.loads(output.read_text(encoding="utf-8"))
    assert not report["complete"] and len(report["cases"]) == 1
    assert report["usage"]["chargedOrReservedTokens"] == 0
    assert report["cases"][0]["passed"] is False
    redis.aclose.assert_awaited_once()


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

    monkeypatch.setattr(station, "build_chain", lambda *args: RunnableLambda(invoke))
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
