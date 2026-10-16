from __future__ import annotations

import logging
from types import SimpleNamespace
from unittest.mock import AsyncMock

import httpx
import pytest
from pydantic import ValidationError

from app.main import app
from app.schemas.models import GrowthSummary, Summary


@pytest.mark.parametrize("claim", [
    "你连续六天签到，积累了 30 点经验。",
    "一次签到和一条分享都留下了足迹。",
    "这周只发了一篇 Vue 技术文章。",
])
def test_narrative_does_not_repeat_stats_or_reject_ordinary_words(claim: str) -> None:
    with pytest.raises(ValidationError):
        Summary(summary=claim, suggestions=["继续分享"])
    value = Summary(
        summary="这一周 Vue3 的分享积累了一点小收获，每一次互动都有意义。", suggestions=["继续分享"]
    )
    assert "Vue3" in value.summary


@pytest.mark.parametrize("claim", ["等级悄悄往上迈了一步。", "等级稳稳地停在原地。"])
def test_growth_does_not_infer_level_changes_from_current_level(claim: str) -> None:
    with pytest.raises(ValidationError):
        GrowthSummary(summary=claim, suggestions=["继续分享"])
    assert GrowthSummary(summary="签到与分享留下了成长足迹。", suggestions=["继续分享"])


async def test_provider_failure_returns_sanitized_error_without_logging_payload(
    caplog: pytest.LogCaptureFixture,
) -> None:
    app_log = logging.getLogger("app")
    app_log.addHandler(caplog.handler)
    app.state.assistant = SimpleNamespace(
        suggest=AsyncMock(side_effect=RuntimeError("private-provider-output"))
    )
    try:
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test"
        ) as client:
            response = await client.post(
                "/ai/internal/tag-suggestions", json={"category": "TECH", "title": "测试", "content": "草稿"}
            )
        assert response.status_code == 503
        assert response.json() == {"code": "AI_UNAVAILABLE"}
        assert "private-provider-output" not in caplog.text
        assert "RuntimeError" in caplog.text
    finally:
        app_log.removeHandler(caplog.handler)
        del app.state.assistant


def test_only_the_offending_sentences_are_dropped() -> None:
    narrative = "整体节奏很稳。收获了 3 篇浏览。继续保持这份分享的习惯。"
    value = Summary(summary=narrative, suggestions=["继续分享"])
    assert value.summary == "整体节奏很稳。继续保持这份分享的习惯。"
    growth = GrowthSummary(summary="分享留下了足迹。等级往上走了。再接再厉。", suggestions=["继续分享"])
    assert growth.summary == "分享留下了足迹。再接再厉。"
