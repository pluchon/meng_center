from __future__ import annotations

import logging
import time
from typing import Any

from pydantic import BaseModel

from app.chains.structured import build_chain, limited
from app.clients.cover_image import CoverImageClient
from app.clients.models import ModelFactory
from app.graphs import cover, moderation, post_analysis
from app.graphs.post_analysis import Context
from app.prompts.templates import VERSION
from app.schemas.models import (
    CoverRequest,
    GrowthSummary,
    Inspirations,
    ModerationInput,
    PostInput,
    Summary,
    Tags,
)

LOG = logging.getLogger(__name__)


class CoverRejected(Exception):
    pass


class Assistant:
    def __init__(self, models: ModelFactory, cover_image: CoverImageClient) -> None:
        self.models = models
        self.cover_image = cover_image
        self.post_graph = post_analysis.build_graph()
        self.moderation_graph = moderation.build_graph()
        self.cover_graph = cover.build_graph()
        definitions = {
            "tags": (Tags, "classifier"),
            "inspirations": (Inspirations, "writer"),
            "growth": (GrowthSummary, "writer"),
            "creator": (Summary, "writer"),
        }
        self.chains = {}
        for name, (schema, role) in definitions.items():
            chain = build_chain(getattr(models, role), schema, name, models.settings.structured_method)
            if role == "writer" and models.settings.writer_model != models.settings.classifier_model:
                chain = chain.with_fallbacks(
                    [
                        limited(
                            build_chain(models.classifier, schema, name, models.settings.structured_method),
                            models.semaphores["classifier"],
                        )
                    ]
                )
            self.chains[name] = chain

    async def suggest(self, task: str, request: BaseModel) -> dict[str, Any]:
        start = time.monotonic()
        role = "classifier" if task == "tags" else "writer"
        # Current levels cannot establish a level change; Java retains these display metrics.
        excluded = {"level", "next_level_experience"} if task == "growth" else set()
        async with self.models.usage() as callback, self.models.semaphores[role]:
            result = await self.chains[task].ainvoke(
                {"payload": request.model_dump_json(by_alias=True, exclude=excluded)},
                config={"callbacks": [callback], "run_name": task, "tags": ["assistant"], "metadata": {}},
            )
        if task == "growth" and len(result.suggestions) != 1:
            result.suggestions = result.suggestions[:1]
        LOG.info(
            "feature=%s model=%s elapsed_ms=%d tokens=%d",
            task,
            getattr(self.models.settings, role + "_model"),
            (time.monotonic() - start) * 1000,
            sum(v.get("total_tokens", 0) for v in callback.usage_metadata.values()),
        )
        return result.model_dump(mode="json", by_alias=True)

    async def analyze(self, post: PostInput) -> dict[str, Any]:
        start = time.monotonic()
        async with self.models.usage() as callback:
            result = await self.post_graph.ainvoke({"post": post}, context=Context(self.models, callback))
        LOG.info(
            "feature=post-analysis post_id=%d model=%s elapsed_ms=%d tokens=%d",
            post.post_id,
            result["result"]["modelVersion"],
            (time.monotonic() - start) * 1000,
            sum(v.get("total_tokens", 0) for v in callback.usage_metadata.values()),
        )
        return result["result"]

    async def moderate(self, item: ModerationInput) -> dict[str, Any]:
        start = time.monotonic()
        async with self.models.usage() as callback:
            result = await self.moderation_graph.ainvoke(
                {"item": item}, context=Context(self.models, callback)
            )
        output = result["result"]
        key = "commentId" if item.comment_id is not None else "userId"
        output.update(
            {
                key: item.comment_id if item.comment_id is not None else item.user_id,
                "version": item.version,
                "modelVersion": f"{VERSION}:{self.models.settings.classifier_model}:"
                f"{self.models.settings.vision_model}",
            }
        )
        LOG.info(
            "feature=moderation subject_id=%d model=%s elapsed_ms=%d tokens=%d",
            output[key],
            output["modelVersion"],
            (time.monotonic() - start) * 1000,
            sum(v.get("total_tokens", 0) for v in callback.usage_metadata.values()),
        )
        return output

    async def image(self, request: CoverRequest) -> bytes:
        start = time.monotonic()
        async with self.models.usage() as callback:
            result = await self.cover_graph.ainvoke(
                {"request": request},
                context=cover.CoverContext(self.models, callback, self.cover_image),
            )
        if not result.get("image"):
            raise CoverRejected
        LOG.info(
            "feature=cover model=%s elapsed_ms=%d tokens=%d",
            self.models.settings.cover_model,
            (time.monotonic() - start) * 1000,
            sum(v.get("total_tokens", 0) for v in callback.usage_metadata.values()),
        )
        return result["image"]
