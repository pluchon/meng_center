from __future__ import annotations

import asyncio
import os
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from datetime import datetime
from zoneinfo import ZoneInfo

from langchain_core.callbacks import UsageMetadataCallbackHandler
from langchain_openai import OpenAIEmbeddings
from redis.asyncio import Redis

from app.clients.model_gateway import CURRENT, BudgetExceeded, GatewayChatOpenAI, ModelGateway
from app.config.settings import Settings


class ModelFactory:
    def __init__(self, settings: Settings, redis: Redis, budget_prefix: str | None = None) -> None:
        os.environ["LANGSMITH_TRACING"] = "false"
        os.environ["LANGCHAIN_TRACING_V2"] = "false"
        self.settings = settings
        self.redis = redis
        self.gateway = ModelGateway(settings, redis, budget_prefix)
        role_models = {
            "classifier": settings.classifier_model,
            "writer": settings.writer_model,
            "vision": settings.vision_model,
            "embedding": settings.embedding_model,
            "cover": settings.cover_model,
            "companion": settings.companion_model,
            "brain": settings.brain_model,
        }
        limits = {model: asyncio.Semaphore(settings.concurrency) for model in set(role_models.values())}
        self.semaphores = {role: limits[model] for role, model in role_models.items()}
        shared = dict(
            base_url=settings.base_url,
            api_key=settings.api_key.get_secret_value(),
            extra_body={"enable_thinking": False},
            max_tokens=1600,
        )
        self.classifier = GatewayChatOpenAI(
            model=settings.classifier_model, temperature=0, timeout=7, max_retries=0, **shared
        ).configure_gateway(self.gateway, "classifier")
        self.graph_classifier = GatewayChatOpenAI(
            model=settings.classifier_model, temperature=0, timeout=7, max_retries=0, **shared
        ).configure_gateway(self.gateway, "classifier")
        self.writer = GatewayChatOpenAI(
            model=settings.writer_model, temperature=0.5, timeout=12, max_retries=0, **shared
        ).configure_gateway(self.gateway, "writer")
        self.companion = GatewayChatOpenAI(
            model=settings.companion_model,
            temperature=settings.companion_temperature,
            timeout=18,
            max_retries=0,
            **{**shared, "max_tokens": 4000},
        ).configure_gateway(self.gateway, "companion")
        self.brain = GatewayChatOpenAI(
            model=settings.brain_model,
            temperature=0.3,
            timeout=12,
            max_retries=0,
            **{**shared, "max_tokens": 2000},
        ).configure_gateway(self.gateway, "brain")
        self.vision = GatewayChatOpenAI(
            model=settings.vision_model, temperature=0, timeout=15, max_retries=0, **shared
        ).configure_gateway(self.gateway, "vision")
        self.embeddings = OpenAIEmbeddings(
            model=settings.embedding_model,
            dimensions=settings.embedding_dimensions,
            check_embedding_ctx_length=False,
            chunk_size=10,
            base_url=settings.base_url,
            api_key=settings.api_key.get_secret_value(),
            request_timeout=1.8,
            max_retries=0,
        )

    @asynccontextmanager
    async def usage(self) -> AsyncIterator[UsageMetadataCallbackHandler]:
        key = self.gateway.daily_key()
        if int(await self.redis.get(key) or 0) >= self.settings.daily_token_limit:
            raise BudgetExceeded
        current = CURRENT.get()
        if current and current.user:
            local_date = datetime.now(ZoneInfo(current.zone)).strftime("%Y%m%d")
            used = int(await self.redis.get(f"meng:ai:user-tokens:{current.user}:{local_date}") or 0)
            if used >= self.settings.user_daily_token_limit:
                raise BudgetExceeded
            current.degraded = used >= self.settings.user_daily_token_limit * 0.8
        callback = UsageMetadataCallbackHandler()
        yield callback

    async def embed(self, texts: list[str]) -> list[list[float]]:
        async with self.semaphores["embedding"]:
            # 字符串输入保留服务商用量信息，网关统一处理限流、重试和熔断。
            response = await self.gateway.invoke(
                self.settings.embedding_model,
                "embedding",
                sum(len(text.encode("utf-8")) for text in texts),
                lambda: self.embeddings.async_client.create(
                    input=texts,
                    model=self.settings.embedding_model,
                    dimensions=self.settings.embedding_dimensions,
                    encoding_format="float",
                ),
                lambda result: {"input_tokens": result.usage.total_tokens, "output_tokens": 0},
            )
            vectors = [item.embedding for item in sorted(response.data, key=lambda item: item.index)]
        if len(vectors) != len(texts) or any(len(v) != self.settings.embedding_dimensions for v in vectors):
            raise ValueError("Embedding shape mismatch")
        return vectors
