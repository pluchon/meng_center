from __future__ import annotations

import json
import logging
from datetime import datetime, timedelta
from uuid import NAMESPACE_URL, uuid5
from zoneinfo import ZoneInfo

import aio_pika
from aio_pika.abc import AbstractIncomingMessage
from pydantic import ValidationError

from app.clients.models import BudgetExceeded
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
        await self.channel.set_qos(prefetch_count=settings.concurrency)
        for domain in ("content", "user", "ai"):
            self.exchanges[domain] = await self.channel.declare_exchange(
                f"meng.{domain}.topic",
                aio_pika.ExchangeType.TOPIC,
                durable=True,
            )
        for route, (domain, _) in ROUTES.items():
            name = "meng.ai." + route
            dead = await self.channel.declare_queue(name + ".dead", durable=True)
            await dead.bind(self.exchanges["ai"], routing_key=route + ".dead")
            queue = await self.channel.declare_queue(
                name,
                durable=True,
                arguments={
                    "x-dead-letter-exchange": "meng.ai.topic",
                    "x-dead-letter-routing-key": route + ".dead",
                },
            )
            await queue.bind(self.exchanges[domain], routing_key=route)
            for suffix, delay in (("retry1", 2000), ("retry2", 8000), ("retry3", 32000)):
                await self.channel.declare_queue(
                    name + "." + suffix,
                    durable=True,
                    arguments={
                        "x-message-ttl": delay,
                        "x-dead-letter-exchange": f"meng.{domain}.topic",
                        "x-dead-letter-routing-key": route,
                    },
                )
            await self.channel.declare_queue(
                name + ".budget",
                durable=True,
                arguments={
                    "x-dead-letter-exchange": f"meng.{domain}.topic",
                    "x-dead-letter-routing-key": route,
                },
            )
            await queue.consume(self.handle)

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
            if isinstance(error, BudgetExceeded):
                now = datetime.now(ZoneInfo(self.settings.timezone))
                tomorrow = now.replace(hour=0, minute=0, second=0, microsecond=0) + timedelta(days=1)
                suffix, expiration = "budget", tomorrow - now
            elif attempts < 3:
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
        if self.connection:
            await self.connection.close()
