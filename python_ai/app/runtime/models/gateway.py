from __future__ import annotations

import asyncio
import json
import logging
import time
from contextvars import ContextVar
from dataclasses import dataclass, field
from datetime import datetime
from zoneinfo import ZoneInfo

from langchain_openai import ChatOpenAI
from pydantic import PrivateAttr

from app.runtime.context.segments import tokens

LOG = logging.getLogger(__name__)


class ModelUnavailable(Exception):
    pass


@dataclass
class RoundUsage:
    user: int = 0
    character: str = ""
    turn: str = ""
    zone: str = "Asia/Taipei"
    tokens: int = 0
    calls: list[dict] = field(default_factory=list)
    segments: dict = field(default_factory=dict)


CURRENT: ContextVar[RoundUsage | None] = ContextVar("model_usage", default=None)
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
        self._key = settings.api_key.get_secret_value()

    def headers(self):
        return {"Authorization": "Bearer " + self._key}

    def provider(self, model):
        return "deepseek" if model.startswith("deepseek-") else "dashscope"

    def chat_options(self, model, thinking=False):
        if self.provider(model) == "deepseek":
            return dict(
                base_url="https://api.deepseek.com",
                api_key=self.settings.deepseek_api_key,
                extra_body={"thinking": {"type": "enabled" if thinking else "disabled"}},
            )
        return dict(
            base_url=self.settings.base_url,
            api_key=self._key,
            extra_body={"enable_thinking": thinking},
        )

    async def acquire(self, model, estimate):
        if self.provider(model) == "deepseek" and not self.settings.deepseek_api_key.get_secret_value():
            raise ModelUnavailable("未配置 DeepSeek 官方密钥")
        if await self.redis.exists(f"meng:ai:circuit:{model}"):
            raise ModelUnavailable("模型熔断中")
        deadline = time.monotonic() + self.settings.model_queue_seconds
        while True:
            if await self.redis.eval(
                TAKE,
                1,
                "meng:ai:ratelimit:account:" + self.provider(model),
                self.settings.requests_per_minute,
                self.settings.tokens_per_minute,
                estimate,
            ):
                return
            if time.monotonic() >= deadline:
                raise ModelUnavailable("模型限流排队超时")
            await asyncio.sleep(0.05)

    async def record(self, model, purpose, usage, elapsed):
        incoming, outgoing = int(usage.get("input_tokens", 0)), int(usage.get("output_tokens", 0))
        cached = int(usage.get("input_token_details", {}).get("cache_read", 0))
        current = CURRENT.get()
        record = dict(
            purpose=purpose,
            model=model,
            provider=self.provider(model),
            input=incoming,
            output=outgoing,
            cached=cached,
            cachedTokens=cached,
            elapsedMs=round(elapsed * 1000),
            cost=(
                (incoming - cached + cached * self.settings.cached_price_ratio)
                * self.settings.input_price_per_million
                + outgoing * self.settings.output_price_per_million
            )
            / 1_000_000,
            images=usage.get("images", 0),
            segments=current.segments if current else {},
            user=current.user if current else 0,
            character=current.character if current else "",
            turn=current.turn if current else "",
        )
        if current:
            current.tokens += incoming + outgoing
            current.calls.append(record)
        # 用量写入失败不能重复已经完成的模型请求。
        try:
            if current and current.user:
                date = datetime.now(ZoneInfo(current.zone)).strftime("%Y%m%d")
                key = f"meng:ai:user-tokens:{current.user}:{date}"
                async with self.redis.pipeline(transaction=True) as pipe:
                    pipe.incrby(key, incoming + outgoing)
                    pipe.expire(key, 172800)
                    await pipe.execute()
            await self.redis.delete(f"meng:ai:failures:{model}")
        except Exception:
            LOG.warning("model_accounting_failed purpose=%s", purpose)
        LOG.info("model_call=%s", json.dumps(record, ensure_ascii=False))

    @staticmethod
    def retryable(error):
        status = getattr(error, "status_code", getattr(getattr(error, "response", None), "status_code", 0))
        return (
            isinstance(error, (TimeoutError, ConnectionError))
            or status in (408, 429, 500, 502, 503, 504)
            or (
                type(error).__name__
                in {"APITimeoutError", "APIConnectionError", "ReadTimeout", "ConnectTimeout", "ConnectError"}
            )
        )

    async def failure(self, model):
        key = f"meng:ai:failures:{model}"
        count = await self.redis.incr(key)
        await self.redis.expire(key, 60)
        if count >= 3:
            await self.redis.set(f"meng:ai:circuit:{model}", "1", ex=self.settings.circuit_seconds)

    async def invoke(self, model, purpose, estimate, call, usage, retry=True):
        start = time.monotonic()
        for attempt in range(2 if retry else 1):
            await self.acquire(model, estimate)
            try:
                result = await call()
            except Exception as error:
                LOG.warning(
                    "model_request_failed purpose=%s model=%s error_type=%s status=%s",
                    purpose,
                    model,
                    type(error).__name__,
                    getattr(error, "status_code", 0),
                )
                await self.failure(model)
                if attempt or not retry or not self.retryable(error):
                    raise
                await asyncio.sleep(0.15)
                continue
            await self.record(model, purpose, usage(result), time.monotonic() - start)
            return result


class GatewayChatOpenAI(ChatOpenAI):
    _gateway: ModelGateway = PrivateAttr()
    _purpose: str = PrivateAttr(default="judge")
    _fallbacks: tuple[str, ...] = PrivateAttr(default=())
    _context_tokens: int = PrivateAttr(default=600_000)
    _clients: dict = PrivateAttr(default_factory=dict)

    def configure_gateway(self, gateway, purpose, fallbacks=(), context_tokens=600_000, clients=None):
        self._gateway, self._purpose, self._fallbacks = gateway, purpose, tuple(fallbacks)
        self._context_tokens = min(600_000, context_tokens)
        self._clients = clients or {}
        return self

    @staticmethod
    def chat_usage(result):
        usage = dict(result.generations[0].message.usage_metadata or {})
        raw = (result.llm_output or {}).get("token_usage", {})
        if raw.get("prompt_cache_hit_tokens") is not None:
            usage["input_token_details"] = {
                **usage.get("input_token_details", {}),
                "cache_read": int(raw["prompt_cache_hit_tokens"]),
            }
        return usage

    async def _agenerate(self, messages, stop=None, run_manager=None, **kwargs):
        estimate = int(self.max_tokens or 1600) + sum(tokens(str(m.content)) for m in messages)
        estimate += tokens(str(kwargs.get("tools", [])))
        if estimate > self._context_tokens:
            raise ModelUnavailable("请求超过模型上下文窗口，需要先压缩历史")
        last = None
        for model in dict.fromkeys((self.model_name, *self._fallbacks)):
            try:

                async def call(selected=model):
                    if selected in self._clients:
                        return await self._clients[selected]._agenerate(
                            messages, stop=stop, run_manager=run_manager, **kwargs
                        )
                    return await super(GatewayChatOpenAI, self)._agenerate(
                        messages, stop=stop, run_manager=run_manager, **{**kwargs, "model": selected}
                    )

                return await self._gateway.invoke(
                    model,
                    self._purpose,
                    estimate,
                    call,
                    self.chat_usage,
                )
            except Exception as error:
                last = error
        raise ModelUnavailable("模型及其降级链均不可用") from last
