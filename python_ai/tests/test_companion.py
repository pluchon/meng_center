from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager
from types import SimpleNamespace
from unittest.mock import AsyncMock

import httpx
import pytest
from pydantic import ValidationError

from app.clients.models import BudgetExceeded
from app.prompts.companion import BOUNDARY, CARDS, system_prompt
from app.schemas.companion import Bubbles, Extraction, ReplyRequest
from app.services.companion import FORBIDDEN, MARKDOWN, SENSITIVE, Companion


def request(**overrides):
    return ReplyRequest(
        **{
            "user_id": 42,
            "character": "TSUNDERE",
            "stage": 1,
            "nickname": "云朵",
            "content": "今天有点累",
            "tools": [],
            **overrides,
        }
    )


@asynccontextmanager
async def usage():
    yield SimpleNamespace(usage_metadata={})


def factory():
    return SimpleNamespace(usage=usage, settings=SimpleNamespace(companion_model="test"))


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
        state = {
            "request": request(tools=["my_growth", "my_recent_posts", "search_posts"]),
            "works": [],
            "callback": None,
            "emit": AsyncMock(),
        }
        tools = {t.name: t for t in service.tools(state)}
        assert set(tools) == {"my_growth", "my_recent_posts", "search_posts"}
        await tools["my_growth"].ainvoke({})
        await tools["my_recent_posts"].ainvoke({})
        await tools["search_posts"].ainvoke({"query": "https://example.org/private?userId=999"})
    assert all(method == "GET" for method, _ in seen)
    assert seen[0][1] == "http://127.0.0.1:8103/growth/internal/users/42/summary"
    assert "/users/42/recent-posts" in seen[1][1]
    assert seen[2][1].startswith("http://127.0.0.1:8102/content/internal/posts/search?")


async def test_tool_failure_does_not_leak_response_or_provider_exception():
    def handler(req):
        return httpx.Response(503, text="secret backend response")

    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as http:
        service = Companion(SimpleNamespace(models=factory()), http)
        tools = service.tools({"request": request(tools=["my_growth"]), "works": [], "emit": AsyncMock()})
        result = await tools[0].ainvoke({})
        assert "没找到" in result and "secret" not in result


def test_cards_cover_all_stages_and_boundaries_come_first():
    assert len(CARDS) == 3
    for code, card in CARDS.items():
        assert len(card.stages) == len(card.openings) == len(card.examples) == 5
        for stage, examples in enumerate(card.examples, 1):
            assert 3 <= len(examples) <= 4
            assert system_prompt(request(character=code, stage=stage)).startswith(BOUNDARY)
        assert not FORBIDDEN.search(card.fallback)
        assert not MARKDOWN.search(card.fallback)


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
            "prompt": "",
            "callback": None,
            "bubbles": ["我在这里"],
            "works": [],
        }
    )
    assert "现实" in "".join(output["bubbles"])
    assert "信得过的人" in "".join(output["bubbles"])


def test_secondary_memory_filter_rejects_obfuscated_contacts_and_health_details():
    for text in ("号码09 12 34 56 78", "09-12-34-56-78", "用户曾经抑郁", "邮箱a@example.com", "微信是hello"):
        assert SENSITIVE.search(text)
    assert not SENSITIVE.search("喜欢蓝色，正在学习 Vue 3")


@pytest.mark.parametrize(
    "content,reply",
    [
        ("已经凌晨两点，还不想睡", "再玩一会儿也行，但别太晚。"),
        ("我先去忙了", "再陪我聊一会儿嘛。"),
    ],
)
async def test_real_life_boundaries_reject_encouraging_late_chat_and_staying(content, reply):
    service = Companion(SimpleNamespace(models=factory()), None)
    service.structured = AsyncMock(side_effect=AssertionError("规则命中后不需要模型判断"))
    assert not await service.safe({"request": request(content=content)}, [reply], [])


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
    assert result == {"bubbles": [CARDS["TSUNDERE"].fallback], "works": []}
    assert await service.extract(state) == {"affection": 0, "memories": []}


async def test_single_event_does_not_become_an_invented_habit():
    service = Companion(SimpleNamespace(models=factory()), None)
    service.structured = AsyncMock(return_value=Extraction(affection=1, memories=["用户习惯凌晨两点聊天"]))
    state = {
        "request": request(content="今天凌晨两点还不太想睡"),
        "callback": None,
        "bubbles": ["早点休息"],
        "label": "PASS",
    }
    assert (await service.extract(state))["memories"] == []
