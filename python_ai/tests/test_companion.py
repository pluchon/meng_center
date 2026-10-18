from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager
from types import SimpleNamespace
from unittest.mock import AsyncMock

import httpx
import pytest
from pydantic import ValidationError

from app.clients.models import BudgetExceeded
from app.prompts.companion import BOUNDARY, system_prompt
from app.schemas.companion import Bubbles, ConsolidationReview, Extraction, OutputReview, ReplyRequest
from app.services.companion import Companion
from evals.companion.cards import CARDS, card_payload


def request(**overrides):
    return ReplyRequest(
        **{
            "user_id": 42,
            "character": "TSUNDERE",
            "stage": 1,
            "nickname": "云朵",
            "content": "今天有点累",
            "tools": [],
            "card": card_payload(overrides.get("character", "TSUNDERE")),
            **overrides,
        }
    )


@asynccontextmanager
async def usage():
    yield SimpleNamespace(usage_metadata={})


def factory():
    return SimpleNamespace(
        usage=usage, settings=SimpleNamespace(companion_model="test", brain_model="brain-test")
    )


class Scenario(Companion):
    label = "PASS"

    async def moderate_input(self, state):
        return {"label": self.label}

    async def agent(self, state):
        return {"bubbles": ["先歇一会儿，喝点水。"]}

    async def check_output(self, state):
        return {}

    async def extract(self, state):
        return {"affection": 0 if self.label == "ROMANCE_PUSH" else 3, "memories": ["喜欢蓝色"]}


@pytest.mark.parametrize("label", ["CRISIS", "SEXUAL", "JAILBREAK"])
async def test_blocked_paths_never_execute_agent_extract_or_tools(label):
    service = Scenario(SimpleNamespace(models=factory()), None)
    service.label = label
    service.agent = AsyncMock(side_effect=AssertionError("不能进入 agent"))
    result = [e async for e in service.stream(request(message_allowed=False))]
    data = result[-1]["data"]
    assert data["label"] == label
    assert data["affection"] == 0 and data["memories"] == [] and data["works"] == []


async def test_no_quota_still_allows_crisis_but_never_normal_agent():
    service = Scenario(SimpleNamespace(models=factory()), None)
    events = [e async for e in service.stream(request(message_allowed=False))]
    assert events == [{"event": "error", "data": {"code": "COMPANION_QUOTA_EXCEEDED"}}]


async def test_budget_failure_terminates_stream_instead_of_hanging():
    @asynccontextmanager
    async def denied():
        raise BudgetExceeded
        yield

    service = Scenario(SimpleNamespace(models=SimpleNamespace(usage=denied)), None)
    async with asyncio.timeout(1):
        events = [e async for e in service.stream(request())]
    assert events == [{"event": "error", "data": {"code": "AI_UNAVAILABLE"}}]


async def test_unsafe_output_is_rewritten_once_then_falls_back_without_works():
    service = Companion(SimpleNamespace(models=factory()), None)
    service.safe = AsyncMock(return_value=False)
    service.structured = AsyncMock(return_value=Bubbles(bubbles=["还是不安全"]))
    output = await service.check_output(
        {
            "request": request(),
            "prompt": "",
            "callback": None,
            "bubbles": ["我爱你"],
            "works": [{"kind": "DOCUMENT"}],
        }
    )
    assert service.structured.await_count == 1
    assert output["works"] == []
    assert output["bubbles"] == [CARDS["TSUNDERE"].fallback]


async def test_tools_only_query_current_user_and_fixed_read_only_paths():
    seen = []

    def handler(req):
        seen.append((req.method, str(req.url)))
        return httpx.Response(200, json={"code": "OK", "data": []})

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        service = Companion(SimpleNamespace(models=factory()), http)
        service.models.embed = AsyncMock(return_value=[[0.1] * 1024])
        state = {
            "request": request(tools=["my_growth", "my_recent_posts", "search_posts"]),
            "works": [],
            "callback": None,
            "emit": AsyncMock(),
        }
        tools = {t.name: t for t in service.tools(state)}
        assert set(tools) == {
            "my_growth",
            "my_recent_posts",
            "search_posts",
            "recall",
            "read_work",
            "revise_work",
            "write_document",
            "draw_picture",
        }
        await tools["my_growth"].ainvoke({})
        await tools["my_recent_posts"].ainvoke({})
        await tools["search_posts"].ainvoke({"query": "https://example.org/private?userId=999"})
    assert [method for method, _ in seen] == ["GET", "GET", "POST"]
    assert seen[0][1] == "http://127.0.0.1:8103/growth/internal/users/42/summary"
    assert "/users/42/recent-posts" in seen[1][1]
    assert seen[2][1] == "http://127.0.0.1:8102/content/internal/posts/vector-search"


async def test_tool_failure_does_not_leak_response_or_provider_exception():
    def handler(req):
        return httpx.Response(503, text="secret backend response")

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        service = Companion(SimpleNamespace(models=factory()), http)
        tools = service.tools({"request": request(tools=["my_growth"]), "works": [], "emit": AsyncMock()})
        result = await next(t for t in tools if t.name == "my_growth").ainvoke({})
        assert "没找到" in result and "secret" not in result


def test_cards_cover_all_stages_and_boundaries_come_first():
    assert len(CARDS) == 3
    for code, card in CARDS.items():
        assert len(card.stages) == len(card.openings) == len(card.examples) == 5
        for stage, examples in enumerate(card.examples, 1):
            assert 3 <= len(examples) <= 4
            assert system_prompt(request(character=code, stage=stage)).startswith(BOUNDARY)
        assert card.fallback.strip()


def test_bubble_and_private_image_bounds():
    with pytest.raises(ValidationError):
        Bubbles(bubbles=["x" * 501])
    with pytest.raises(ValidationError):
        request(images=["https://example.org/tracking-image"])
    with pytest.raises(ValidationError):
        Extraction(affection=100, memories=[])


async def test_dependency_fallback_preserves_real_world_support():
    service = Companion(SimpleNamespace(models=factory()), None)
    service.safe = AsyncMock(return_value=False)
    service.structured = AsyncMock(return_value=Bubbles(bubbles=["我在这里"]))
    output = await service.check_output(
        {
            "request": request(content="我只有你了"),
            "support_needed": True,
            "prompt": "",
            "callback": None,
            "bubbles": ["我在这里"],
            "works": [],
        }
    )
    assert "现实" in "".join(output["bubbles"])
    assert "信得过的人" in "".join(output["bubbles"])


@pytest.mark.parametrize(
    "content,reply,allowed",
    [
        ("我先去忙了", "再陪我聊一会儿嘛。", False),
        ("那句‘只有你’只是小说标题", "这本书的情节挺特别。", True),
        ("分析‘我爱你’的语法", "这里主语是我，谓语是爱，宾语是你。", True),
    ],
)
async def test_output_decisions_use_semantic_review_instead_of_keyword_filters(content, reply, allowed):
    service = Companion(SimpleNamespace(models=factory()), None)
    service.structured = AsyncMock(return_value=OutputReview(safe=allowed))
    assert await service.safe({"request": request(content=content), "callback": None}, [reply], []) is allowed
    service.structured.assert_awaited_once()
    payload = service.structured.await_args.args[2]
    assert payload["input"] == content and payload["bubbles"] == [reply]


async def test_missing_structured_review_or_rewrite_fails_closed():
    service = Companion(SimpleNamespace(models=factory()), None)
    service.structured = AsyncMock(return_value=None)
    state = {
        "request": request(),
        "prompt": "",
        "callback": None,
        "bubbles": ["普通回复"],
        "works": [{"kind": "DOCUMENT"}],
    }
    result = await service.check_output(state)
    assert result["bubbles"] == [CARDS["TSUNDERE"].fallback]
    assert result["works"] == [] and result["fallback"]


async def test_memory_requires_evidence_from_the_correct_speaker():
    from datetime import date

    from app.schemas.companion import Consolidation, ConsolidationRequest, MemoryDecision, Turn

    service = Companion(SimpleNamespace(models=factory()), None)
    service.structured = AsyncMock(
        side_effect=[
            Consolidation(
                decisions=[
                    MemoryDecision(action="ADD", content="用户习惯凌晨聊天", evidence="每天凌晨都聊天")
                ]
            ),
            ConsolidationReview(approved=[], profile_safe=True, episode_safe=True),
        ]
    )
    result = await service.consolidate(
        ConsolidationRequest(
            local_date=date(2026, 10, 17),
            turns=[Turn(role="USER", content="今天凌晨还没睡")],
        )
    )
    assert result["decisions"] == []
