from __future__ import annotations

import base64
from dataclasses import dataclass
from io import BytesIO
from typing import Any, TypedDict

from langgraph.graph import END, START, StateGraph
from langgraph.runtime import Runtime
from langgraph.types import RetryPolicy
from PIL import Image

from app.clients.cover_image import CoverImageClient
from app.graphs.post_analysis import Context, moderate_image, retryable
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
    writer = factory.task_chains["cover"]
    async with factory.semaphores["writer"]:
        result = await writer.ainvoke(
            {"payload": payload(state["request"])},
            config={"callbacks": [runtime.context.callback], "run_name": "cover-plan", "tags": ["cover"]},
        )
    return {"plan": result}


async def check(state: State, runtime: Runtime[CoverContext]) -> dict[str, Moderation]:
    factory = runtime.context.models
    chain = factory.task_chains["moderate"]
    async with factory.semaphores["judge"]:
        result = await chain.ainvoke(
            {"payload": payload(state["plan"])},
            config={"callbacks": [runtime.context.callback], "run_name": "cover-check", "tags": ["cover"]},
        )
    return {"review": result}


async def generate(state: State, runtime: Runtime[CoverContext]) -> dict[str, bytes]:
    async with runtime.context.models.semaphores["image"]:
        image = await runtime.context.cover_image.generate(state["plan"])
    with Image.open(BytesIO(image)) as source:
        source.thumbnail((512, 512))
        thumbnail = BytesIO()
        source.convert("RGB").save(thumbnail, format="JPEG")
    review = await moderate_image(
        {"image": {"id": 0, "data": base64.b64encode(thumbnail.getvalue()).decode()}}, runtime
    )
    return {"image": image} if review["reviews"][0]["verdict"] == "PASS" else {}


def route(state: State) -> str:
    return "generate" if state["review"].verdict == Verdict.PASS else END


def build_graph() -> Any:
    graph = StateGraph(State, context_schema=CoverContext)
    graph.add_node("plan", plan)
    graph.add_node("check", check)
    graph.add_node("generate", generate, retry_policy=RetryPolicy(max_attempts=2, retry_on=retryable))
    graph.add_edge(START, "plan")
    graph.add_edge("plan", "check")
    graph.add_conditional_edges("check", route, ["generate", END])
    graph.add_edge("generate", END)
    return graph.compile()
