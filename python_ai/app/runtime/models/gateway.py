from __future__ import annotations

import asyncio
import json
import logging
import time
from contextvars import ContextVar
from dataclasses import dataclass, field
from datetime import datetime
from zoneinfo import ZoneInfo

from langchain_core.messages import SystemMessage
from langchain_core.runnables import RunnableLambda
from langchain_openai import ChatOpenAI
from pydantic import PrivateAttr

from app.runtime.context.cache import changes, digest, fingerprints, public_hashes
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
    session: int = 0


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
        return "dashscope"

    def chat_options(self, model, thinking=False):
        return dict(
            base_url=self.settings.base_url,
            api_key=self._key,
            extra_body={"enable_thinking": thinking},
        )

    async def acquire(self, model, estimate):
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
        cache = usage.get("_cache_segments")
        record = dict(
            purpose=purpose,
            model=model,
            provider=self.provider(model),
            input=incoming,
            output=outgoing,
            cached=cached,
            cachedTokens=cached,
            cacheCreationTokens=int(usage.get("input_token_details", {}).get("cache_creation", 0)),
            elapsedMs=round(elapsed * 1000),
            cost=(
                (incoming - cached + cached * self.settings.cached_price_ratio)
                * self.settings.input_price_per_million
                + outgoing * self.settings.output_price_per_million
            )
            / 1_000_000,
            images=usage.get("images", 0),
            searchCalls=int(usage.get("search_calls", 0)),
            reasoningTokens=usage.get("output_token_details", {}).get("reasoning"),
            segments=dict(usage.get("_segments", {})),
            user=current.user if current else 0,
            character=current.character if current else "",
            turn=current.turn if current else "",
            **usage.get("_task", {}),
        )
        if purpose == "speech" and cache:
            record["segmentHashes"] = public_hashes(cache)
            try:
                async with asyncio.timeout(0.2):
                    diagnostic = await self.cache_diagnostic(model, cache, incoming, cached, current)
                if diagnostic:
                    record["cacheDiagnostic"] = diagnostic
            except Exception:
                LOG.warning("speech_cache_diagnostic_failed")
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

    async def cache_diagnostic(self, model, cache, incoming, cached, current):
        if not current or not current.session or not current.turn:
            return None
        scope = digest([current.user, current.character, current.session, model])
        key = "meng:ai:cache-prefix:" + scope
        raw = await self.redis.get(key)
        previous = json.loads(raw) if isinstance(raw, (str, bytes)) else None
        # 接话与重写属于同一轮，不能把它们当成下一轮的热会话样本。
        if previous and previous.get("turn") == current.turn:
            return None
        diagnostic = changes(previous, cache) if previous else None
        await self.redis.set(key, json.dumps({**public_hashes(cache), "turn": current.turn}), ex=3600)
        if diagnostic and incoming > 0 and cached / incoming < 0.5:
            diagnostic.update(cacheRatio=cached / incoming)
            LOG.warning("speech_cache_break=%s", json.dumps(diagnostic, ensure_ascii=False))
        return diagnostic

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
        class RetryableRequest(Exception):
            pass

        async def invoke_once(_):
            await self.acquire(model, estimate)
            start = time.monotonic()
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
                if retry and self.retryable(error):
                    raise RetryableRequest() from error
                raise
            await self.record(model, purpose, usage(result), time.monotonic() - start)
            return result

        operation = RunnableLambda(invoke_once).with_retry(
            stop_after_attempt=2 if retry else 1,
            retry_if_exception_type=(RetryableRequest,),
            exponential_jitter_params={"multiplier": 0.15, "max": 0.5},
        )
        try:
            return await operation.ainvoke(None)
        except RetryableRequest as error:
            # 网关已经重试一次，外层状态图不能再重复这一批请求。
            raise ModelUnavailable("模型请求重试后仍不可用") from error.__cause__


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
        usage["search_calls"] = sum(
            isinstance(block, dict) and block.get("type") == "web_search_call"
            for block in result.generations[0].message.content
        ) if isinstance(result.generations[0].message.content, list) else 0
        raw = (result.llm_output or {}).get("token_usage", {})
        created = (raw.get("prompt_tokens_details") or {}).get("cache_creation_input_tokens")
        if created is not None:
            usage["input_token_details"] = {
                **usage.get("input_token_details", {}), "cache_creation": int(created),
            }
        return usage

    def cached_messages(self, model, messages):
        # 只为已确认支持显式缓存的主决策模型标记稳定规则；不缓存滚动窗口或修改正文。
        if (
            self._purpose != "agent" or model != "qwen3.8-flash" or not messages
            or not isinstance(messages[0], SystemMessage)
            or not isinstance(messages[0].content, str)
            or tokens(messages[0].content) < 1024
        ):
            return messages
        stable = messages[0].model_copy(update={"content": [{
            "type": "text", "text": messages[0].content,
            "cache_control": {"type": "ephemeral"},
        }]})
        return [stable, *messages[1:]]

    async def _agenerate(self, messages, stop=None, run_manager=None, **kwargs):
        retries = kwargs.pop("_runtime_retries", 1)
        graph_retry = kwargs.pop("_graph_retry", False)
        metadata = getattr(run_manager, "metadata", {}) or {}
        estimate = int(self.max_tokens or 1600) + sum(tokens(str(m.content)) for m in messages)
        estimate += tokens(str(kwargs.get("tools", [])))
        if estimate > self._context_tokens:
            raise ModelUnavailable("请求超过模型上下文窗口，需要先压缩历史")

        def operation(model):
            request_messages = self.cached_messages(model, messages)

            async def invoke(_):
                async def call():
                    if model in self._clients:
                        return await self._clients[model]._agenerate(
                            request_messages, stop=stop, run_manager=run_manager, **kwargs
                        )
                    return await super(GatewayChatOpenAI, self)._agenerate(
                        request_messages, stop=stop, run_manager=run_manager, **kwargs
                    )

                return await self._gateway.invoke(
                    model,
                    self._purpose,
                    estimate,
                    call,
                    lambda result: {
                        **self.chat_usage(result),
                        "_segments": metadata.get("contextSegments", {}),
                        "_task": {
                            key: metadata[key]
                            for key in ("task", "taskVersion", "evaluationSet")
                            if key in metadata
                        },
                        **({"_cache_segments": fingerprints(messages)} if self._purpose == "speech" else {}),
                    },
                    retry=self._purpose != "agent" and retries > 0,
                )

            return RunnableLambda(invoke)

        primary, *fallbacks = [
            operation(model) for model in dict.fromkeys((self.model_name, *self._fallbacks))
        ]
        try:
            result = await primary.with_fallbacks(fallbacks).ainvoke(None)
            if self._purpose == "speech":
                result.generations[0].message.response_metadata["contextBudget"] = {
                    "limit": self._context_tokens,
                    "estimated": estimate,
                    # 下一轮用户原文最多 1000 字，另保留一次完整生成的空间。
                    "compactAfterRound": estimate + int(self.max_tokens or 1600) + 1000
                    >= self._context_tokens,
                }
            return result
        except Exception as error:
            if self._purpose == "agent" or graph_retry:
                raise
            raise ModelUnavailable("模型及其降级链均不可用") from error
