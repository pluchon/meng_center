from __future__ import annotations

import asyncio
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from langchain_core.callbacks import UsageMetadataCallbackHandler
from langchain_core.embeddings import DeterministicFakeEmbedding
from langchain_core.language_models.fake_chat_models import GenericFakeChatModel
from langchain_core.messages import AIMessage
from langchain_core.runnables import RunnableLambda
from pydantic import SecretStr

from app.clients.models import ModelFactory
from app.config.settings import Settings
from app.graphs import moderation, post_analysis
from app.graphs.cover import CoverContext
from app.graphs.cover import build_graph as cover_graph
from app.graphs.post_analysis import Context
from app.schemas.models import CoverRequest, ModerationInput, PostInput, TrainingRequest
from app.services.assistant import Assistant
from app.services.ranker import train


class ToolModel(GenericFakeChatModel):
    def bind_tools(self, tools: list, **kwargs: object) -> ToolModel:
        return self


def model(schema: str, value: dict) -> ToolModel:
    message = AIMessage(content="", tool_calls=[{"name": schema, "args": value, "id": "test"}])
    return ToolModel(messages=iter([message] * 20))


def factory(verdict: str = "PASS") -> SimpleNamespace:
    embeddings = DeterministicFakeEmbedding(size=1024)

    async def embed(texts: list[str], text_type: str) -> list[list[float]]:
        return await embeddings.aembed_documents(texts)

    result = SimpleNamespace(
        judge=model(
            "Understanding",
            {
                "topics": ["Vue"],
                "keywords": ["前端"],
                "quality": 3,
                "verdict": verdict,
                "labels": [],
                "reason": "正常讨论",
            },
        ),
        vision=model("Moderation", {"verdict": "SUSPECT", "labels": ["暴力"], "reason": "待人工确认"}),
        embed=embed,
        semaphores={
            role: asyncio.Semaphore(2) for role in ("judge", "vision", "writer", "image", "review", "assist")
        },
        settings=SimpleNamespace(
            structured_method="function_calling",
            judge_model="fake",
            writer_model="fake",
            vision_model="fake",
            embedding_model="fake",
            embedding_dimensions=1024,
        ),
    )
    result.review = model("Moderation", {"verdict": "BLOCK", "labels": [], "reason": "明确违规"})
    result.writer = result.assist = result.judge
    return result


def prepare(models):
    from app.chains.structured import build_chain
    from app.runtime.tasks.registry import TASKS

    models.task_chains = {
        name: build_chain(getattr(models, spec.purpose), spec.schema, name, "function_calling")
        for name, spec in TASKS.items()
        if name in {"understand", "moderate", "cover"}
    }
    return models


def post() -> PostInput:
    return PostInput(
        post_id=12,
        author_id=5,
        version="2026-10-16T02:00:00.123456",
        category="TECH",
        title="Vue 性能优化",
        plain_text="测试内容",
        tags=["Vue"],
    )


async def test_analysis_returns_complete_vector_and_preserves_version() -> None:
    result = await post_analysis.build_graph().ainvoke(
        {"post": post()},
        context=Context(prepare(factory()), UsageMetadataCallbackHandler()),
    )
    assert len(result["result"]["embedding"]) == 1024
    assert result["result"]["version"] == post().version
    assert result["result"]["verdict"] == "PASS"
    assert result["result"]["analyzeState"] == "SUCCESS"


async def test_missing_embedding_never_emits_partial_success() -> None:
    models = factory()
    models.embed = AsyncMock(side_effect=RuntimeError("provider unavailable"))
    with pytest.raises(RuntimeError):
        await post_analysis.build_graph().ainvoke(
            {"post": post()},
            context=Context(prepare(models), UsageMetadataCallbackHandler()),
        )


async def test_text_failure_uses_neutral_features_but_keeps_vector() -> None:
    models = factory()
    models.judge = ToolModel(messages=iter(["invalid output"] * 10))
    result = await post_analysis.build_graph().ainvoke(
        {"post": post()},
        context=Context(prepare(models), UsageMetadataCallbackHandler()),
    )
    assert result["result"]["quality"] == 3
    assert result["result"]["analyzeState"] == "FAILED"
    assert len(result["result"]["embedding"]) == 1024


async def test_moderation_block_wins_over_other_reviews() -> None:
    models = factory()
    models.judge = model("Moderation", {"verdict": "BLOCK", "labels": ["广告"], "reason": "恶意广告"})
    result = await moderation.build_graph().ainvoke(
        {"item": ModerationInput(comment_id=3, content="待审核的文字")},
        context=Context(prepare(models), UsageMetadataCallbackHandler()),
    )
    assert result["result"]["verdict"] == "BLOCK"
    assert result["result"]["labels"] == ["广告"]


async def test_parallel_image_review_is_retained_in_post_result() -> None:
    item = PostInput.model_validate({**post().model_dump(), "image_thumbs": [{"id": 8, "data": "/9j/"}]})
    result = await post_analysis.build_graph().ainvoke(
        {"post": item}, context=Context(prepare(factory()), UsageMetadataCallbackHandler())
    )
    assert result["result"]["verdict"] == "SUSPECT"
    assert result["result"]["imageReviews"][0]["imageId"] == 8
    assert "image:8:暴力" in result["result"]["labels"]
    assert len(result["result"]["embedding"]) == 1024


async def test_profile_moderation_keeps_avatar_review_for_java_reuse() -> None:
    models = factory()
    models.judge = model("Moderation", {"verdict": "PASS", "labels": [], "reason": "正常资料"})
    result = await moderation.build_graph().ainvoke(
        {
            "item": ModerationInput(
                user_id=9, nickname="云朵", avatar_changed=True, avatar_thumb={"id": 9, "data": "/9j/"}
            )
        },
        context=Context(prepare(models), UsageMetadataCallbackHandler()),
    )
    assert result["result"]["avatarChanged"] is True
    assert result["result"]["verdict"] == "SUSPECT"
    assert result["result"]["imageReviews"][0]["imageId"] == 9


async def test_rejected_cover_never_calls_image_provider() -> None:
    models = factory()
    models.writer = model("CoverPlan", {"prompt": "unsafe", "negativePrompt": ""})
    models.judge = model("Moderation", {"verdict": "BLOCK", "labels": ["暴力"], "reason": "不可生成"})
    cover_image = SimpleNamespace(generate=AsyncMock())
    result = await cover_graph().ainvoke(
        {"request": CoverRequest(title="标题", summary="摘要", category="DAILY")},
        context=CoverContext(prepare(models), UsageMetadataCallbackHandler(), cover_image),
    )
    assert "image" not in result
    cover_image.generate.assert_not_called()


def test_ranker_skips_insufficient_samples() -> None:
    request = TrainingRequest(
        samples=[
            {"features": [0.1, 1], "clicked": False, "occurredAt": "2026-10-16T00:00:00Z"},
            {"features": [0.9, 2], "clicked": True, "occurredAt": "2026-10-16T01:00:00Z"},
        ]
    )
    assert train(request)["eligible"] is False


async def test_same_writer_model_does_not_retry_as_its_own_fallback(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    calls = []

    async def unavailable(payload: object) -> None:
        calls.append(payload)
        raise RuntimeError("unavailable")

    monkeypatch.setattr("app.services.assistant.build_chain", lambda *args: RunnableLambda(unavailable))
    models = factory()
    models.writer = models.judge
    assistant = Assistant(models, SimpleNamespace())
    with pytest.raises(RuntimeError, match="unavailable"):
        await assistant.chains["growth"].ainvoke({"payload": "{}"})
    assert len(calls) == 1


async def test_same_model_roles_share_one_concurrency_budget() -> None:
    models = ModelFactory(
        Settings.model_construct(
            api_key=SecretStr("unit-test"),
            deepseek_api_key=SecretStr("deepseek-test"),
            concurrency=1,
            writer_model="qwen3.8-flash",
        ),
        AsyncMock(),
    )
    active = peak = 0

    async def call(role: str) -> None:
        nonlocal active, peak
        async with models.semaphores[role]:
            active += 1
            peak = max(peak, active)
            await asyncio.sleep(0)
            active -= 1

    await asyncio.gather(*(call(role) for role in ("judge", "writer", "vision")))
    assert peak == 1
