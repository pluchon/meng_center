from __future__ import annotations

import asyncio
import json
import logging
import time
from collections.abc import Awaitable, Callable
from contextvars import ContextVar
from dataclasses import dataclass, field
from datetime import datetime
from zoneinfo import ZoneInfo

from langchain_openai import ChatOpenAI
from pydantic import PrivateAttr

LOG = logging.getLogger(__name__)


class BudgetExceeded(Exception):
    pass


@dataclass
class RoundUsage:
    user: int = 0
    character: str = ""
    turn: str = ""
    zone: str = "Asia/Taipei"
    tokens: int = 0
    calls: list[dict] = field(default_factory=list)
    degraded: bool = False


CURRENT: ContextVar[RoundUsage | None] = ContextVar("companion_usage", default=None)

# Redis TIME 统一各实例的时钟；按账号共享 RPM/TPM，桶随空闲自动恢复。
TAKE = """
local tm=redis.call('TIME')
local now=tonumber(tm[1])+tonumber(tm[2])/1000000
local last=tonumber(redis.call('HGET',KEYS[1],'last') or now)
local rpm=tonumber(ARGV[1]) local tpm=tonumber(ARGV[2]) local cost=tonumber(ARGV[3])
local r=math.min(rpm,tonumber(redis.call('HGET',KEYS[1],'r') or rpm)+(now-last)*rpm/60)
local t=math.min(tpm,tonumber(redis.call('HGET',KEYS[1],'t') or tpm)+(now-last)*tpm/60)
if r<1 or t<cost then return 0 end
redis.call('HSET',KEYS[1],'r',r-1,'t',t-cost,'last',now)
redis.call('EXPIRE',KEYS[1],120)
return 1
"""


class ModelGateway:
    def __init__(self, settings, redis):
        self.settings, self.redis = settings, redis

    async def acquire(self, model: str, estimate: int) -> None:
        current = CURRENT.get()
        if current and current.tokens + estimate > self.settings.round_token_limit:
            current.degraded = True
            raise BudgetExceeded
        date = datetime.now(ZoneInfo(self.settings.timezone)).strftime("%Y%m%d")
        if int(await self.redis.get(f"ai:tokens:{date}") or 0) >= self.settings.daily_token_limit:
            raise BudgetExceeded
        if await self.redis.exists(f"meng:ai:circuit:{model}"):
            raise RuntimeError("model circuit open")
        deadline = time.monotonic() + self.settings.model_queue_seconds
        while True:
            allowed = await self.redis.eval(
                TAKE,
                1,
                "meng:ai:ratelimit:account",
                self.settings.requests_per_minute,
                self.settings.tokens_per_minute,
                estimate,
            )
            if allowed:
                return
            if time.monotonic() >= deadline:
                raise BudgetExceeded
            await asyncio.sleep(0.05)

    async def record(self, model: str, role: str, usage: dict, elapsed: float) -> None:
        input_tokens = int(usage.get("input_tokens", 0))
        output_tokens = int(usage.get("output_tokens", 0))
        cached = int(usage.get("input_token_details", {}).get("cache_read", 0))
        total = input_tokens + output_tokens
        cost = (
            (input_tokens - cached + cached * self.settings.cached_price_ratio)
            * self.settings.input_price_per_million
            + output_tokens * self.settings.output_price_per_million
        ) / 1_000_000
        record = {
            "role": role,
            "model": model,
            "input": input_tokens,
            "output": output_tokens,
            "cached": cached,
            "cost": cost,
            "elapsedMs": round(elapsed * 1000),
            "priceConfigured": self.settings.input_price_per_million > 0
            or self.settings.output_price_per_million > 0,
            "images": usage.get("images", 0),
        }
        current = CURRENT.get()
        record.update(
            {
                "turn": current.turn if current else "",
                "user": current.user if current else 0,
                "character": current.character if current else "",
            }
        )
        if total:
            date = datetime.now(ZoneInfo(self.settings.timezone)).strftime("%Y%m%d")
            async with self.redis.pipeline(transaction=True) as pipe:
                await pipe.incrby(f"ai:tokens:{date}", total).expire(f"ai:tokens:{date}", 172800).execute()
        if current:
            current.tokens += total
            current.calls.append(record)
            if current.user:
                date = datetime.now(ZoneInfo(current.zone)).strftime("%Y%m%d")
                key = f"meng:ai:user-tokens:{current.user}:{date}"
                async with self.redis.pipeline(transaction=True) as pipe:
                    await pipe.incrby(key, total).expire(key, 172800).execute()
        await self.redis.delete(f"meng:ai:failures:{model}")
        LOG.info("model_call=%s", json.dumps(record, ensure_ascii=False))

    @staticmethod
    def retryable(error) -> bool:
        status = getattr(error, "status_code", getattr(getattr(error, "response", None), "status_code", 0))
        return (
            isinstance(error, (TimeoutError, ConnectionError))
            or status in (408, 429, 500, 502, 503, 504)
            or type(error).__name__
            in (
                "APITimeoutError",
                "APIConnectionError",
                "ReadTimeout",
                "ConnectTimeout",
                "ConnectError",
            )
        )

    async def invoke(
        self, model: str, role: str, estimate: int, call: Callable[[], Awaitable], usage, retry=True
    ):
        start = time.monotonic()
        for attempt in range(2 if retry else 1):
            await self.acquire(model, estimate)
            try:
                result = await call()
            except Exception as error:
                await self.failure(model)
                if attempt or not retry or not self.retryable(error):
                    raise
                await asyncio.sleep(0.15)
                continue
            await self.record(model, role, usage(result), time.monotonic() - start)
            return result

    async def failure(self, model: str) -> None:
        key = f"meng:ai:failures:{model}"
        count = await self.redis.incr(key)
        await self.redis.expire(key, 60)
        if count >= 3:
            await self.redis.set(f"meng:ai:circuit:{model}", "1", ex=self.settings.circuit_seconds)


class GatewayChatOpenAI(ChatOpenAI):
    _gateway: ModelGateway = PrivateAttr()
    _role: str = PrivateAttr(default="classifier")

    def configure_gateway(self, gateway: ModelGateway, role: str):
        self._gateway, self._role = gateway, role
        return self

    async def _agenerate(self, messages, stop=None, run_manager=None, **kwargs):
        from app.services.companion_context import tokens

        estimate = int(self.max_tokens or 1600)
        for message in messages:
            if isinstance(message.content, str):
                estimate += tokens(message.content)
            else:
                estimate += sum(
                    1024 if item.get("type") == "image_url" else tokens(item.get("text", ""))
                    for item in message.content
                    if isinstance(item, dict)
                )
        start = time.monotonic()
        for attempt in range(2):
            await self._gateway.acquire(self.model_name, estimate)
            try:
                result = await super()._agenerate(messages, stop=stop, run_manager=run_manager, **kwargs)
            except Exception as error:
                await self._gateway.failure(self.model_name)
                if attempt or not self._gateway.retryable(error):
                    raise
                await asyncio.sleep(0.15)
                continue
            for generation in result.generations:
                await self._gateway.record(
                    self.model_name,
                    self._role,
                    generation.message.usage_metadata or {},
                    time.monotonic() - start,
                )
            return result
