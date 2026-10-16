from __future__ import annotations

import json
from types import SimpleNamespace
from unittest.mock import AsyncMock

from app.clients.models import BudgetExceeded
from app.consumers.events import Consumers


def message(attempt: int = 0) -> SimpleNamespace:
    body = {
        "eventId": "source-1",
        "eventType": "post.published",
        "traceId": "trace",
        "occurredAt": "2026-10-16T00:00:00Z",
        "body": {
            "postId": 1,
            "authorId": 2,
            "version": "2026-10-16T00:00:00",
            "title": "测试",
            "plainText": "正文",
            "category": "DAILY",
            "tags": [],
            "imageThumbs": [],
        },
    }
    return SimpleNamespace(
        body=json.dumps(body).encode(),
        routing_key="post.published",
        headers={"meng-attempt": attempt},
        ack=AsyncMock(),
        reject=AsyncMock(),
        nack=AsyncMock(),
    )


def consumer(error: Exception | None = None) -> Consumers:
    assistant = SimpleNamespace(analyze=AsyncMock(side_effect=error, return_value={"postId": 1}))
    worker = Consumers(SimpleNamespace(timezone="Asia/Taipei"), assistant)
    worker.channel = SimpleNamespace(default_exchange=SimpleNamespace(publish=AsyncMock()))
    worker.exchanges = {"ai": SimpleNamespace(publish=AsyncMock())}
    return worker


async def test_result_identity_stays_stable_after_redelivery() -> None:
    worker = consumer()
    for _ in range(2):
        item = message()
        await worker.handle(item)
        item.ack.assert_awaited_once()
    first, second = worker.exchanges["ai"].publish.call_args_list
    assert json.loads(first.args[0].body)["eventId"] == json.loads(second.args[0].body)["eventId"]


async def test_retry_is_confirmed_before_original_ack() -> None:
    worker = consumer(RuntimeError("temporary provider failure"))
    item = message(1)
    await worker.handle(item)
    assert worker.channel.default_exchange.publish.call_args.kwargs["routing_key"].endswith("retry2")
    item.ack.assert_awaited_once()
    worker = consumer(RuntimeError("temporary provider failure"))
    worker.channel.default_exchange.publish.side_effect = ConnectionError()
    item = message()
    await worker.handle(item)
    item.ack.assert_not_awaited()
    item.nack.assert_awaited_once_with(requeue=True)


async def test_exhausted_message_is_dead_lettered() -> None:
    worker = consumer(RuntimeError("provider unavailable"))
    item = message(3)
    await worker.handle(item)
    item.reject.assert_awaited_once_with(requeue=False)
    item.ack.assert_not_awaited()


async def test_budget_deferral_does_not_consume_retry_attempt() -> None:
    worker = consumer(BudgetExceeded())
    item = message(2)
    await worker.handle(item)
    call = worker.channel.default_exchange.publish.call_args
    assert call.kwargs["routing_key"].endswith("budget")
    assert call.args[0].headers["meng-attempt"] == 2
    item.ack.assert_awaited_once()


async def test_invalid_message_never_calls_model() -> None:
    worker = consumer()
    item = message()
    item.body = b"{}"
    await worker.handle(item)
    worker.assistant.analyze.assert_not_awaited()
    item.reject.assert_awaited_once_with(requeue=False)
