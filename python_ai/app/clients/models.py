import asyncio
import os
from contextlib import asynccontextmanager

import httpx
from langchain_core.callbacks import UsageMetadataCallbackHandler
from langchain_openai import ChatOpenAI

from app.clients.embedding import EmbeddingClient
from app.clients.rerank import RerankClient
from app.runtime.models.gateway import GatewayChatOpenAI, ModelGateway
from app.runtime.models.registry import registry


class ModelFactory:
    def __init__(self, settings, redis, http=None, gateway=None):
        os.environ["LANGSMITH_TRACING"] = "false"
        os.environ["LANGCHAIN_TRACING_V2"] = "false"
        self.settings, self.redis = settings, redis
        self.gateway = gateway or ModelGateway(settings, redis)
        self.registry = registry(settings)
        limits, self.semaphores = {}, {}
        for purpose, spec in self.registry.items():
            self.semaphores[purpose] = limits.setdefault(spec.model, asyncio.Semaphore(settings.concurrency))
            if purpose in {"embedding", "rerank", "image"}:
                continue

            def options(model, spec=spec, purpose=purpose):
                return dict(
                    model=model,
                    temperature=spec.temperature,
                    timeout=spec.timeout,
                    max_tokens=spec.max_tokens,
                    max_retries=0,
                    n=None if purpose == "search" else 1,
                    **self.gateway.chat_options(model, spec.thinking),
                    **({"use_responses_api": True, "output_version": "responses/v1"}
                       if purpose == "search" else {}),
                )

            setattr(
                self,
                purpose,
                GatewayChatOpenAI(
                    **options(spec.model),
                ).configure_gateway(
                    self.gateway,
                    purpose,
                    spec.fallbacks,
                    spec.context_tokens,
                    {model: ChatOpenAI(**options(model)) for model in spec.fallbacks if model != spec.model},
                ),
            )
        self.http = http or httpx.AsyncClient()
        self.embedding_client = EmbeddingClient(settings, self.http, self.gateway)
        self.rerank = RerankClient(settings, self.http, self.gateway)

    @asynccontextmanager
    async def usage(self):
        yield UsageMetadataCallbackHandler()

    async def embed(self, texts, text_type):
        async with self.semaphores["embedding"]:
            return await self.embedding_client.encode(texts, text_type)
