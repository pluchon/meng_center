from __future__ import annotations

import logging
import time
from typing import Any

from pydantic import BaseModel

from app.clients.cover_image import CoverImageClient
from app.clients.models import ModelFactory
from app.graphs import cover, moderation, post_analysis
from app.graphs.post_analysis import Context
from app.prompts.templates import TASKS as PROMPTS
from app.prompts.templates import VERSION
from app.runtime.tasks.registry import TASKS, payload
from app.runtime.tasks.structured import TaskRuntime
from app.runtime.tasks.tags import normalize
from app.schemas.models import (
    CoverRequest,
    ModerationInput,
    PostInput,
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
        self.tasks = TaskRuntime(models)
        self.chains = {name: self.tasks.chain(name) for name in PROMPTS}
        models.task_runtime = self.tasks
        models.task_chains = self.chains

    async def suggest(self, task: str, request: BaseModel) -> dict[str, Any]:
        start = time.monotonic()
        role = TASKS[task].purpose
        # Current levels cannot establish a level change; Java retains these display metrics.
        excluded = {"level", "next_level_experience"} if task == "growth" else set()
        async with self.models.usage() as callback, self.models.semaphores[role]:
            result = await self.chains[task].ainvoke(
                {"payload": payload(request, excluded)},
                config={"callbacks": [callback], "run_name": task, "tags": ["assistant"], "metadata": {}},
            )
        if task == "tags":
            names = normalize([tag.name for tag in result.tags], request.candidates)
            result.tags = [
                tag.model_copy(update={"name": name, "existing": name in request.candidates})
                for name in names
                for tag in result.tags
                if normalize([tag.name], request.candidates)[0] == name
            ][:3]
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
                "modelVersion": f"{VERSION}:{self.models.settings.judge_model}:"
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
