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
    def __init__(self, settings, redis, budget_prefix: str | None = None):
        self.settings, self.redis = settings, redis
        self.budget_prefix = budget_prefix

    def daily_key(self) -> str:
        if self.budget_prefix:
            return self.budget_prefix
        date = datetime.now(ZoneInfo(self.settings.timezone)).strftime("%Y%m%d")
        return f"ai:tokens:{date}"

    async def acquire(self, model: str, estimate: int) -> str:
        budget_key = self.daily_key()
        current = CURRENT.get()
        if current and current.tokens + estimate > self.settings.round_token_limit:
            current.degraded = True
            raise BudgetExceeded
        if int(await self.redis.get(budget_key) or 0) >= self.settings.daily_token_limit:
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
                # 每种预算都先原子预留，避免并行请求分别看到余额而共同超额。
                # 评测预算按固定编号累计，不能通过重启或跨日重新获得已授权的额度。
                reserved = await self.redis.eval(
                    "local n=tonumber(redis.call('GET',KEYS[1]) or 0); "
                    "if n+tonumber(ARGV[1])>tonumber(ARGV[2]) then return 0 end; "
                    "redis.call('INCRBY',KEYS[1],ARGV[1]); "
                    "if ARGV[3]=='daily' then redis.call('EXPIRE',KEYS[1],172800) end; return 1",
                    1,
                    budget_key,
                    estimate,
                    self.settings.daily_token_limit,
                    "fixed" if self.budget_prefix else "daily",
                )
                if not reserved:
                    raise BudgetExceeded
                return budget_key
            if time.monotonic() >= deadline:
                raise BudgetExceeded
            await asyncio.sleep(0.05)

    async def record(
        self,
        model: str,
        role: str,
        usage: dict,
        elapsed: float,
        reserved: int = 0,
        budget_key: str | None = None,
    ) -> None:
        # 跨过午夜的请求必须结算回预留时的预算键。
        budget_key = budget_key or self.daily_key()
        input_tokens = int(usage.get("input_tokens", 0))
        output_tokens = int(usage.get("output_tokens", 0))
        cached = int(usage.get("input_token_details", {}).get("cache_read", 0))
        total = input_tokens + output_tokens if "input_tokens" in usage else reserved
        cost = (
            (input_tokens - cached + cached * self.settings.cached_price_ratio)
            * self.settings.input_price_per_million
            + output_tokens * self.settings.output_price_per_million
        ) / 1_000_000
        record = {
            "budgetKey": budget_key,
            "accountedTokens": total,
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
        if total or reserved:
            delta = total - reserved
            async with self.redis.pipeline(transaction=True) as pipe:
                pipe.incrby(budget_key, delta)
                if not self.budget_prefix:
                    pipe.expire(budget_key, 172800)
                await pipe.execute()
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
            budget_key = await self.acquire(model, estimate)
            try:
                result = await call()
            except Exception as error:
                await self.failure(model)
                if attempt or not retry or not self.retryable(error):
                    raise
                await asyncio.sleep(0.15)
                continue
            await self.record(
                model, role, usage(result), time.monotonic() - start, reserved=estimate, budget_key=budget_key
            )
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
        estimate = int(self.max_tokens or 1600)
        for message in messages:
            estimate += 64
            if isinstance(message.content, str):
                estimate += len(message.content.encode("utf-8"))
            else:
                estimate += sum(
                    2048 if item.get("type") == "image_url" else len(item.get("text", "").encode("utf-8"))
                    for item in message.content
                    if isinstance(item, dict)
                )
        estimate += len(json.dumps(kwargs, ensure_ascii=False, default=str).encode("utf-8"))
        start = time.monotonic()
        for attempt in range(2):
            budget_key = await self._gateway.acquire(self.model_name, estimate)
            try:
                result = await super()._agenerate(messages, stop=stop, run_manager=run_manager, **kwargs)
            except Exception as error:
                await self._gateway.failure(self.model_name)
                if attempt or not self._gateway.retryable(error):
                    raise
                await asyncio.sleep(0.15)
                continue
            for index, generation in enumerate(result.generations):
                await self._gateway.record(
                    self.model_name,
                    self._role,
                    generation.message.usage_metadata or {},
                    time.monotonic() - start,
                    reserved=estimate if index == 0 else 0,
                    budget_key=budget_key,
                )
            return result
