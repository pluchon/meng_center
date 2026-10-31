from __future__ import annotations

import json

from fastapi import APIRouter, Request
from fastapi.responses import StreamingResponse

from app.schemas.companion import (
    ConsolidationRequest,
    EmbeddingRequest,
    ReplyRequest,
    SummaryRequest,
    VoiceRequest,
)

router = APIRouter(prefix="/ai/internal/companion")


@router.post("/replies")
async def replies(body: ReplyRequest, request: Request):
    async def events():
        async for item in request.app.state.companion.stream(body):
            yield f"event: {item['event']}\ndata: {json.dumps(item['data'], ensure_ascii=False)}\n\n"

    return StreamingResponse(events(), media_type="text/event-stream", headers={"Cache-Control": "no-store"})


@router.post("/summaries")
async def summaries(body: SummaryRequest, request: Request):
    return await request.app.state.companion.summary(body)


@router.post("/consolidations")
async def consolidations(body: ConsolidationRequest, request: Request):
    return await request.app.state.companion.consolidate(body)


@router.post("/voices")
async def voices(body: VoiceRequest, request: Request):
    return await request.app.state.companion.models.voice.synthesize(body.text, body.mood, body.lead)


@router.post("/embeddings")
async def embeddings(body: EmbeddingRequest, request: Request):
    return await request.app.state.companion.embeddings(body)
