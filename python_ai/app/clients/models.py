from __future__ import annotations

import asyncio
import os
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from datetime import datetime
from zoneinfo import ZoneInfo

from langchain_core.callbacks import UsageMetadataCallbackHandler
from langchain_openai import ChatOpenAI, OpenAIEmbeddings
from redis.asyncio import Redis

from app.config.settings import Settings


class BudgetExceeded(Exception):
    pass


class ModelFactory:
    def __init__(self, settings: Settings, redis: Redis) -> None:
        os.environ["LANGSMITH_TRACING"] = "false"
        os.environ["LANGCHAIN_TRACING_V2"] = "false"
        self.settings = settings
        self.redis = redis
        role_models = {
            "classifier": settings.classifier_model,
            "writer": settings.writer_model,
            "vision": settings.vision_model,
            "embedding": settings.embedding_model,
            "cover": settings.cover_model,
            "companion": settings.companion_model,
        }
        limits = {model: asyncio.Semaphore(settings.concurrency) for model in set(role_models.values())}
        self.semaphores = {role: limits[model] for role, model in role_models.items()}
        shared = dict(
            base_url=settings.base_url,
            api_key=settings.api_key.get_secret_value(),
            extra_body={"enable_thinking": False},
            max_tokens=1600,
        )
        self.classifier = ChatOpenAI(
            model=settings.classifier_model, temperature=0, timeout=7, max_retries=2, **shared
        )
        self.graph_classifier = ChatOpenAI(
            model=settings.classifier_model, temperature=0, timeout=7, max_retries=0, **shared
        )
        self.writer = ChatOpenAI(
            model=settings.writer_model, temperature=0.5, timeout=12, max_retries=2, **shared
        )
        self.companion = ChatOpenAI(
            model=settings.companion_model,
            temperature=settings.companion_temperature,
            timeout=18,
            max_retries=0,
            **{**shared, "max_tokens": 4000},
        )
        self.vision = ChatOpenAI(
            model=settings.vision_model, temperature=0, timeout=15, max_retries=0, **shared
        )
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
        date = datetime.now(ZoneInfo(self.settings.timezone)).strftime("%Y%m%d")
        key = f"ai:tokens:{date}"
        if int(await self.redis.get(key) or 0) >= self.settings.daily_token_limit:
            raise BudgetExceeded
        callback = UsageMetadataCallbackHandler()
        try:
            yield callback
        finally:
            total = sum(item.get("total_tokens", 0) for item in callback.usage_metadata.values())
            if total:
                async with self.redis.pipeline(transaction=True) as pipe:
                    await pipe.incrby(key, total).expire(key, 172800).execute()

    async def embed(self, texts: list[str]) -> list[list[float]]:
        async with self.semaphores["embedding"]:
            # Use the LangChain client's string-input path, retaining provider usage metadata.
            response = await self.embeddings.async_client.create(
                input=texts,
                model=self.settings.embedding_model,
                dimensions=self.settings.embedding_dimensions,
                encoding_format="float",
            )
            vectors = [item.embedding for item in sorted(response.data, key=lambda item: item.index)]
            date = datetime.now(ZoneInfo(self.settings.timezone)).strftime("%Y%m%d")
            async with self.redis.pipeline(transaction=True) as pipe:
                await (
                    pipe.incrby(f"ai:tokens:{date}", response.usage.total_tokens)
                    .expire(f"ai:tokens:{date}", 172800)
                    .execute()
                )
        if len(vectors) != len(texts) or any(len(v) != self.settings.embedding_dimensions for v in vectors):
            raise ValueError("Embedding shape mismatch")
        return vectors
