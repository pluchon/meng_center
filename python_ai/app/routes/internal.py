from __future__ import annotations

import asyncio

from fastapi import APIRouter, HTTPException, Request, Response

from app.schemas.models import (
    CoverRequest,
    CreatorStats,
    Draft,
    EmbeddingsRequest,
    GrowthStats,
    TagRequest,
    TrainingRequest,
)
from app.schemas.retrieval import RetrievalRequest
from app.services.ranker import train

router = APIRouter(prefix="/ai/internal")


@router.post("/retrievals")
async def retrievals(body: RetrievalRequest, request: Request) -> dict:
    return await request.app.state.companion.retrieve(body)


@router.post("/embeddings")
async def embeddings(body: EmbeddingsRequest, request: Request) -> dict:
    models = request.app.state.assistant.models
    async with models.usage():
        vectors = await models.embed(body.texts)
    return {
        "vectors": vectors,
        "modelVersion": f"{models.settings.embedding_model}:{models.settings.embedding_dimensions}",
    }


@router.post("/tag-suggestions")
async def tags(body: TagRequest, request: Request) -> dict:
    if not (body.title.strip() or body.content.strip()):
        raise HTTPException(400, "EMPTY_DRAFT")
    return await request.app.state.assistant.suggest("tags", body)


@router.post("/inspirations")
async def inspirations(body: Draft, request: Request) -> dict:
    if not (body.title.strip() or body.content.strip()):
        raise HTTPException(400, "EMPTY_DRAFT")
    return await request.app.state.assistant.suggest("inspirations", body)


@router.post("/covers")
async def covers(body: CoverRequest, request: Request) -> Response:
    if not (body.title.strip() or body.summary.strip()):
        raise HTTPException(400, "EMPTY_DRAFT")
    data = await request.app.state.assistant.image(body)
    return Response(data, media_type="image/png")


@router.post("/growth-summaries")
async def growth(body: GrowthStats, request: Request) -> dict:
    return await request.app.state.assistant.suggest("growth", body)


@router.post("/creator-reviews")
async def creator(body: CreatorStats, request: Request) -> dict:
    return await request.app.state.assistant.suggest("creator", body)


@router.post("/ranker-trainings")
async def ranker(body: TrainingRequest, request: Request) -> dict:
    async with request.app.state.training_lock:
        return await asyncio.to_thread(train, body)
