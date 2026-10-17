from __future__ import annotations

import asyncio
import logging
import os
from collections.abc import AsyncIterator, Awaitable, Callable
from contextlib import AsyncExitStack, asynccontextmanager

import httpx
from fastapi import FastAPI, Request, Response
from fastapi.responses import JSONResponse
from pydantic import ValidationError
from redis.asyncio import Redis

from app.clients.cover_image import CoverImageClient
from app.clients.discovery import Discovery
from app.clients.models import BudgetExceeded, ModelFactory
from app.config.settings import Settings
from app.consumers.events import Consumers
from app.routes.companion import router as companion_router
from app.routes.internal import router
from app.services.assistant import Assistant, CoverRejected
from app.services.companion import Companion

LOG = logging.getLogger(__name__)
APP_LOG = logging.getLogger("app")
APP_LOG.setLevel(logging.INFO)
APP_LOG.propagate = False
if not APP_LOG.handlers:
    handler = logging.StreamHandler()
    handler.setFormatter(logging.Formatter("%(levelname)s %(name)s %(message)s"))
    APP_LOG.addHandler(handler)
os.environ["LANGSMITH_TRACING"] = "false"
os.environ["LANGCHAIN_TRACING_V2"] = "false"


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    try:
        settings = Settings()
    except ValidationError:
        raise RuntimeError(
            "AI settings are missing or invalid; check the documented environment variables"
        ) from None
    async with AsyncExitStack() as stack:
        redis = Redis(
            host=settings.redis_host,
            port=settings.redis_port,
            password=settings.redis_password.get_secret_value(),
            decode_responses=True,
        )
        stack.push_async_callback(redis.aclose)
        await redis.ping()
        http = await stack.enter_async_context(httpx.AsyncClient(follow_redirects=False))
        assistant = Assistant(ModelFactory(settings, redis), CoverImageClient(settings, http))
        consumers = Consumers(settings, assistant)
        stack.push_async_callback(consumers.close)
        await consumers.start()
        discovery = Discovery(settings)
        stack.push_async_callback(discovery.close)
        await discovery.start()
        app.state.assistant = assistant
        app.state.companion = Companion(assistant, http)
        app.state.training_lock = asyncio.Semaphore(1)
        yield


app = FastAPI(
    title="meng-ai internal computation", lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None
)
app.include_router(router)
app.include_router(companion_router)


@app.get("/actuator/health")
async def health() -> dict[str, str]:
    return {"status": "UP"}


@app.exception_handler(BudgetExceeded)
async def budget_error(request: Request, error: BudgetExceeded) -> JSONResponse:
    return JSONResponse({"code": "AI_UNAVAILABLE"}, status_code=503)


@app.exception_handler(CoverRejected)
async def cover_error(request: Request, error: CoverRejected) -> JSONResponse:
    return JSONResponse({"code": "AI_CONTENT_REJECTED"}, status_code=422)


@app.middleware("http")
async def compute_boundary(request: Request, call_next: Callable[[Request], Awaitable[Response]]) -> Response:
    try:
        return await call_next(request)
    except Exception as error:
        # Handle before ServerErrorMiddleware can print provider output or input in a traceback.
        LOG.warning("request_failed path=%s error_type=%s", request.url.path, type(error).__name__)
        return JSONResponse({"code": "AI_UNAVAILABLE"}, status_code=503)
