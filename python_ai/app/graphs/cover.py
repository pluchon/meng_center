from __future__ import annotations

import base64
from dataclasses import dataclass
from io import BytesIO
from typing import Any, TypedDict

from langgraph.graph import END, START, StateGraph
from langgraph.runtime import Runtime
from PIL import Image

from app.clients.cover_image import CoverImageClient
from app.graphs.moderation import Context, review_image, review_text
from app.runtime.models.retry import MODEL_RETRY
from app.runtime.tasks.registry import payload
from app.schemas.models import CoverPlan, CoverRequest, Moderation, Verdict


@dataclass
class CoverContext(Context):
    cover_image: CoverImageClient


class State(TypedDict, total=False):
    request: CoverRequest
    plan: CoverPlan
    review: Moderation
    image: bytes


async def plan(state: State, runtime: Runtime[CoverContext]) -> dict[str, CoverPlan]:
    factory = runtime.context.models
    writer = factory.task_chains["cover_plan"]
    async with factory.semaphores["writer"]:
        result = await writer.ainvoke(
            {"payload": payload(state["request"])},
            config={
                "callbacks": [runtime.context.callback],
                "run_name": "cover-plan",
                "tags": ["cover_plan"],
            },
        )
    return {"plan": result}


async def check(state: State, runtime: Runtime[CoverContext]) -> dict[str, Moderation]:
    result = await review_text(runtime.context.models, payload(state["plan"]), runtime.context.callback)
    return {"review": result}


async def generate(state: State, runtime: Runtime[CoverContext]) -> dict[str, bytes]:
    async with runtime.context.models.semaphores["image"]:
        image = await runtime.context.cover_image.generate(state["plan"])
    return {"image": image}


async def review(state: State, runtime: Runtime[CoverContext]):
    with Image.open(BytesIO(state["image"])) as source:
        source.thumbnail((512, 512))
        thumbnail = BytesIO()
        source.convert("RGB").save(thumbnail, format="JPEG")
    checked = await review_image(
        runtime.context.models,
        {"id": 0, "data": base64.b64encode(thumbnail.getvalue()).decode()},
        runtime.context.callback,
    )
    return {} if checked.verdict == Verdict.PASS else {"image": b""}


def route(state: State) -> str:
    return "generate" if state["review"].verdict == Verdict.PASS else END


def build_graph() -> Any:
    graph = StateGraph(State, context_schema=CoverContext)
    graph.add_node("plan", plan, retry_policy=MODEL_RETRY)
    graph.add_node("check", check, retry_policy=MODEL_RETRY)
    graph.add_node("review", review, retry_policy=MODEL_RETRY)
    graph.add_node("generate", generate, retry_policy=MODEL_RETRY)
    graph.add_edge(START, "plan")
    graph.add_edge("plan", "check")
    graph.add_conditional_edges("check", route, ["generate", END])
    graph.add_edge("generate", "review")
    graph.add_edge("review", END)
    return graph.compile()
