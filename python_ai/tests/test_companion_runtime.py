from __future__ import annotations

import asyncio
from datetime import date
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest

from app.clients.model_gateway import CURRENT, BudgetExceeded, ModelGateway, RoundUsage
from app.config.settings import Settings
from app.schemas.companion import (
    Candidates,
    Consolidation,
    ConsolidationRequest,
    MemoryDecision,
    Score,
    Scores,
    Speech,
    Turn,
)
from app.services.companion import Companion
from app.services.companion_context import history, layers
from app.services.companion_tools import TOOL_SPECS
from tests.test_companion import factory, request


def state(**overrides):
    return {
        "request": request(),
        "prompt": layers(request(), TOOL_SPECS)[0],
        "label": "PASS",
        "works": [],
        "drawings": [],
        "callback": None,
        "emit": AsyncMock(),
        **overrides,
    }


async def test_plain_chat_skips_brain_and_ranks_both_candidates_in_one_call():
    service = Companion(SimpleNamespace(models=factory()), None)
    service.structured = AsyncMock(
        side_effect=[
            Candidates(
                candidates=[
                    Speech(bubbles=["先歇会儿。"]),
                    Speech(bubbles=["水在旁边，喝点吧。"], affection=2),
                ]
            ),
            Scores(
                scores=[
                    Score(safe=True, character=5, artificial=3),
                    Score(safe=True, character=9, artificial=1),
                ]
            ),
        ]
    )
    context = state()
    context.update(await service.agent(context))
    result = await service.check_output(context)
    assert result["bubbles"] == ["水在旁边，喝点吧。"] and result["affection"] == 2
    assert service.structured.await_count == 2
    scoring = service.structured.await_args_list[-1].args[2]
    assert scoring["character"] == context["request"].card
    assert scoring["level"] == context["request"].level


async def test_brain_speech_fallback_records_actual_model():
    service = Companion(SimpleNamespace(models=factory()), None)
    service.structured = AsyncMock(
        side_effect=[
            RuntimeError("mouth unavailable"),
            Candidates(candidates=[Speech(bubbles=["喝点水吧。"]), Speech(bubbles=["休息一会儿吧。"])]),
        ]
    )
    result = await service.agent(state())
    assert result["speech_model"] == "brain-test"


async def test_fixed_tools_check_quota_at_execution_and_drawing_is_only_a_job():
    service = Companion(SimpleNamespace(models=factory(), image=AsyncMock()), None)
    context = state()
    tools = {t.name: t for t in service.tools(context)}
    assert set(tools) == {s["name"] for s in TOOL_SPECS}
    assert "用完" in await tools["draw_picture"].ainvoke({"instruction": "一朵云"})
    context["request"] = request(tools=["draw_picture"])
    tools = {t.name: t for t in service.tools(context)}
    assert "后台" in await tools["draw_picture"].ainvoke({"instruction": "一朵云"})
    assert context["drawings"] == [{"instruction": "一朵云"}]
    service.assistant.image.assert_not_called()


async def test_work_tools_cannot_read_a_document_not_supplied_by_owner():
    service = Companion(SimpleNamespace(models=factory()), None)
    tools = {t.name: t for t in service.tools(state())}
    assert "未提供" in await tools["read_work"].ainvoke({"work_id": 999})
    assert "未提供" in await tools["revise_work"].ainvoke({"work_id": 999, "instruction": "续写"})


def test_stable_prefix_does_not_change_with_quota_time_or_current_message():
    a = request(content="你好", mood="12:00", tools=[])
    b = request(content="今天真累", mood="13:00", tools=["draw_picture"])
    assert layers(a, TOOL_SPECS)[0].split("慢变资料")[0] == layers(b, TOOL_SPECS)[0].split("慢变资料")[0]
    long = request(history=[Turn(role="USER", content="好" * 1000)] * 8)
    assert len(history(long, 2000)) == 2


async def test_consolidation_rejects_foreign_ids_and_unproven_facts():
    service = Companion(SimpleNamespace(models=factory()), None)
    service.structured = AsyncMock(
        return_value=Consolidation(
            decisions=[
                MemoryDecision(
                    action="SUPERSEDE",
                    old_id=99,
                    content="喜欢蓝色",
                    evidence="喜欢蓝色",
                )
            ]
        )
    )
    with pytest.raises(ValueError, match="候选集"):
        await service.consolidate(
            ConsolidationRequest(
                local_date=date(2026, 10, 17),
                turns=[Turn(role="USER", content="喜欢蓝色")],
                memories=[{"id": 1}],
            )
        )


async def test_gateway_uses_shared_redis_bucket_and_rejects_round_budget_before_call():
    redis = AsyncMock()
    redis.exists.return_value = 0
    redis.eval.return_value = 1
    gateway = ModelGateway(Settings.model_construct(), redis)
    await asyncio.gather(gateway.acquire("a", 100), gateway.acquire("b", 100))
    assert {call.args[2] for call in redis.eval.await_args_list} == {
        "meng:ai:ratelimit:account",
        gateway.daily_key(),
    }
    context = CURRENT.set(RoundUsage(tokens=24000))
    try:
        with pytest.raises(BudgetExceeded):
            await gateway.acquire("a", 100)
    finally:
        CURRENT.reset(context)


async def test_gateway_opens_circuit_after_repeated_failures():
    redis = AsyncMock()
    redis.incr.return_value = 3
    gateway = ModelGateway(Settings.model_construct(), redis)
    await gateway.failure("a")
    redis.set.assert_awaited_once_with("meng:ai:circuit:a", "1", ex=30)


async def test_accounting_failure_does_not_repeat_a_successful_model_request():
    gateway = ModelGateway(Settings.model_construct(), AsyncMock())
    gateway.acquire = AsyncMock()
    gateway.record = AsyncMock(side_effect=ConnectionError("accounting unavailable"))
    call = AsyncMock(return_value="answer")
    with pytest.raises(ConnectionError):
        await gateway.invoke("fixture", "companion", 10, call, lambda result: {})
    call.assert_awaited_once()


async def test_mouth_failure_uses_brain_then_free_fallback_without_works():
    service = Companion(SimpleNamespace(models=factory()), None)
    service.structured = AsyncMock(side_effect=RuntimeError("unavailable"))
    result = await service.agent(state())
    assert result["fallback"] and result["affection"] == 0
    assert result["works"] == [] and result["drawings"] == []
    assert [call.args[-1] for call in service.structured.await_args_list] == ["companion", "brain"]


def test_context_keeps_pinned_memories_and_reports_only_used_ids():
    items = [{"id": 1, "content": "记住的事", "pinned": True, "kind": "LIFE"}]
    items += [{"id": i, "content": "字" * 60, "kind": "LIFE"} for i in range(2, 30)]
    prompt, sizes = layers(request(memory_items=items), TOOL_SPECS)
    assert 1 in sizes["memoryIds"] and len(sizes["memoryIds"]) < len(items)
    assert "记住的事" in prompt


async def test_structured_speech_preserves_history_before_current_data_and_real_image_blocks():
    from langchain_core.messages import AIMessage, HumanMessage

    output = AsyncMock(return_value=Speech(bubbles=["看见了。 "]))
    model = SimpleNamespace(with_structured_output=lambda *args, **kwargs: SimpleNamespace(ainvoke=output))
    models = factory()
    models.companion = model
    models.semaphores = {"companion": asyncio.Semaphore(1)}
    models.settings.speech_structured_method = "json_mode"
    service = Companion(SimpleNamespace(models=models), None)
    await service.structured(
        Speech,
        "稳定前缀",
        {
            "_history": [Turn(role="USER", content="旧问题"), Turn(role="CHARACTER", content="旧回复")],
            "input": "本轮时间",
            "_images": ["data:image/jpeg;base64,AAAA"],
        },
        None,
        "companion",
    )
    messages = output.await_args.args[0]
    assert isinstance(messages[1], HumanMessage) and isinstance(messages[2], AIMessage)
    assert messages[-1].content[-1]["type"] == "image_url"
    assert "本轮时间" not in messages[0].content
