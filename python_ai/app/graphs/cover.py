from __future__ import annotations

from dataclasses import dataclass
from typing import Any, TypedDict

from langgraph.graph import END, START, StateGraph
from langgraph.runtime import Runtime
from langgraph.types import RetryPolicy

from app.chains.structured import build_chain, limited
from app.clients.cover_image import CoverImageClient
from app.graphs.post_analysis import Context, retryable
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
    writer = build_chain(factory.writer, CoverPlan, "cover", factory.settings.structured_method)
    if factory.settings.writer_model != factory.settings.classifier_model:
        fallback = build_chain(factory.classifier, CoverPlan, "cover", factory.settings.structured_method)
        writer = writer.with_fallbacks([limited(fallback, factory.semaphores["classifier"])])
    async with factory.semaphores["writer"]:
        result = await writer.ainvoke(
            {"payload": state["request"].model_dump_json()},
            config={"callbacks": [runtime.context.callback], "run_name": "cover-plan", "tags": ["cover"]},
        )
    return {"plan": result}


async def check(state: State, runtime: Runtime[CoverContext]) -> dict[str, Moderation]:
    factory = runtime.context.models
    chain = build_chain(factory.classifier, Moderation, "moderate", factory.settings.structured_method)
    async with factory.semaphores["classifier"]:
        result = await chain.ainvoke(
            {"payload": state["plan"].model_dump_json()},
            config={"callbacks": [runtime.context.callback], "run_name": "cover-check", "tags": ["cover"]},
        )
    return {"review": result}


async def generate(state: State, runtime: Runtime[CoverContext]) -> dict[str, bytes]:
    async with runtime.context.models.semaphores["cover"]:
        return {"image": await runtime.context.cover_image.generate(state["plan"])}


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
