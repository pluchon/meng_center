from __future__ import annotations

import asyncio
import json
import logging
from datetime import datetime
from uuid import NAMESPACE_URL, uuid5
from zoneinfo import ZoneInfo

import aio_pika
from aio_pika.abc import AbstractIncomingMessage
from pydantic import ValidationError

from app.config.settings import Settings
from app.schemas.models import Event, ModerationInput, PostInput
from app.services.assistant import Assistant

LOG = logging.getLogger(__name__)
ROUTES = {
    "post.published": ("content", "post.analyzed"),
    "post.updated": ("content", "post.analyzed"),
    "post.analysis-requested": ("content", "post.analyzed"),
    "comment.created": ("content", "comment.moderated"),
    "user.profile-updated": ("user", "user.profile-moderated"),
}


class InvalidEvent(Exception):
    pass


class Consumers:
    def __init__(self, settings: Settings, assistant: Assistant) -> None:
        self.settings = settings
        self.assistant = assistant
        self.connection = None
        self.channel = None
        self.exchanges = {}

    async def start(self) -> None:
        settings = self.settings
        self.connection = await aio_pika.connect_robust(
            host=settings.rabbit_host,
            port=settings.rabbit_port,
            login=settings.rabbit_username,
            password=settings.rabbit_password.get_secret_value(),
            virtualhost="meng",
        )
        self.channel = await self.connection.channel(publisher_confirms=True, on_return_raises=True)
        await self.channel.set_qos(prefetch_count=settings.concurrency, global_=True)
        for domain in ("content", "user", "ai"):
            self.exchanges[domain] = await self.channel.declare_exchange(
                f"meng.{domain}.topic",
                aio_pika.ExchangeType.TOPIC,
                durable=True,
            )
        self.batch_channel = await self.connection.channel(publisher_confirms=True, on_return_raises=True)
        await self.batch_channel.set_qos(prefetch_count=1)
        self.realtime_queues = []
        for route, (domain, _) in ROUTES.items():
            channel = self.batch_channel if route == "post.analysis-requested" else self.channel
            name = "meng.ai." + route
            dead = await channel.declare_queue(name + ".dead", durable=True)
            await dead.bind(self.exchanges["ai"], routing_key=route + ".dead")
            queue = await channel.declare_queue(
                name,
                durable=True,
                arguments={
                    "x-dead-letter-exchange": "meng.ai.topic",
                    "x-dead-letter-routing-key": route + ".dead",
                },
            )
            await queue.bind(self.exchanges[domain], routing_key=route)
            for suffix, delay in (("retry1", 2000), ("retry2", 8000), ("retry3", 32000)):
                await channel.declare_queue(
                    name + "." + suffix,
                    durable=True,
                    arguments={
                        "x-message-ttl": delay,
                        "x-dead-letter-exchange": f"meng.{domain}.topic",
                        "x-dead-letter-routing-key": route,
                    },
                )
            if route != "post.analysis-requested":
                self.realtime_queues.append(queue)
            if route == "post.analysis-requested":
                self.batch_task = asyncio.create_task(self.batch(queue))
            else:
                await queue.consume(self.handle)

    async def batch(self, queue):
        while True:
            try:
                backlogged = False
                for realtime in self.realtime_queues:
                    status = await self.channel.declare_queue(realtime.name, passive=True)
                    backlogged |= bool(status.declaration_result.message_count)
                if backlogged:
                    await asyncio.sleep(0.25)
                    continue
                message = await queue.get(fail=False, timeout=1)
                if message is None:
                    await asyncio.sleep(0.1)
                else:
                    await self.handle(message)
            except Exception as error:
                LOG.warning("batch_poll_failed type=%s", type(error).__name__)
                await asyncio.sleep(1)

    async def handle(self, message: AbstractIncomingMessage) -> None:
        event = None
        try:
            if len(message.body) > 6_000_000:
                raise InvalidEvent("Event too large")
            event = Event.model_validate_json(message.body)
            if event.event_type not in ROUTES:
                raise InvalidEvent("Unknown event")
            expected_domain, result_type = ROUTES[event.event_type]
            if message.routing_key != event.event_type:
                raise InvalidEvent("Event routing mismatch")
            if event.event_type.startswith("post."):
                result = await self.assistant.analyze(PostInput.model_validate(event.body))
            else:
                result = await self.assistant.moderate(ModerationInput.model_validate(event.body))
            result_event = Event(
                event_id=str(uuid5(NAMESPACE_URL, event.event_id + ":" + result_type)),
                event_type=result_type,
                occurred_at=datetime.now(ZoneInfo("UTC")),
                trace_id=event.trace_id,
                body=result,
            )
            await self.exchanges["ai"].publish(
                aio_pika.Message(
                    body=result_event.model_dump_json(by_alias=True).encode(),
                    content_type="application/json",
                    delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
                    message_id=result_event.event_id,
                ),
                routing_key=result_type,
                mandatory=True,
            )
            await message.ack()
        except (ValidationError, InvalidEvent, json.JSONDecodeError) as error:
            LOG.warning("event_invalid type=%s", type(error).__name__)
            await message.reject(requeue=False)
        except Exception as error:
            route = event.event_type if event else message.routing_key
            attempts = int((message.headers or {}).get("meng-attempt", 0))
            if attempts < 3:
                suffix, expiration = f"retry{attempts + 1}", None
                attempts += 1
            else:
                LOG.error(
                    "event_exhausted event_id=%s type=%s",
                    event.event_id if event else "",
                    type(error).__name__,
                )
                await message.reject(requeue=False)
                return
            LOG.warning(
                "event_deferred event_id=%s queue=%s type=%s",
                event.event_id if event else "",
                suffix,
                type(error).__name__,
            )
            try:
                await self.channel.default_exchange.publish(
                    aio_pika.Message(
                        body=message.body,
                        content_type="application/json",
                        delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
                        headers={"meng-attempt": attempts},
                        expiration=expiration,
                    ),
                    routing_key=f"meng.ai.{route}.{suffix}",
                    mandatory=True,
                )
                await message.ack()
            except Exception:
                await message.nack(requeue=True)

    async def close(self) -> None:
        task = getattr(self, "batch_task", None)
        if task:
            task.cancel()
            await asyncio.gather(task, return_exceptions=True)
        if self.connection:
            await self.connection.close()
