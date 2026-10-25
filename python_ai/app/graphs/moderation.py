from __future__ import annotations

import operator
from typing import Annotated, Any, TypedDict

from langgraph.graph import END, START, StateGraph
from langgraph.runtime import Runtime
from langgraph.types import Send

from app.graphs.post_analysis import RETRY, Context, moderate_image
from app.runtime.safety.escalate import confirm
from app.runtime.tasks.registry import payload
from app.schemas.models import ModerationInput


class State(TypedDict, total=False):
    item: ModerationInput
    reviews: Annotated[list[dict[str, Any]], operator.add]
    result: dict[str, Any]


async def text(state: State, runtime: Runtime[Context]) -> dict[str, Any]:
    factory = runtime.context.models
    item = state["item"]
    chain = factory.task_chains["moderate"]
    async with factory.semaphores["judge"]:
        review = await chain.ainvoke(
            {"payload": payload(item, {"image_thumbs", "avatar_thumb"})},
            config={
                "callbacks": [runtime.context.callback],
                "run_name": "text-moderation",
                "tags": ["moderation"],
                "metadata": {"comment_id": item.comment_id, "user_id": item.user_id},
            },
        )
    review = await confirm(
        factory, review, payload(item, {"image_thumbs", "avatar_thumb"}), runtime.context.callback
    )
    return {"reviews": [review.model_dump(mode="json")]}


def dispatch(state: State) -> list[Send]:
    item = state["item"]
    images = item.image_thumbs + ([item.avatar_thumb] if item.avatar_thumb else [])
    return [Send("text", state)] + [Send("image", {"image": image.model_dump()}) for image in images]


def merge(state: State) -> dict[str, Any]:
    severity = {"PASS": 0, "SUSPECT": 1, "BLOCK": 2}
    reviews = state["reviews"]
    strongest = max(reviews, key=lambda review: severity[review["verdict"]])
    labels = []
    for review in reviews:
        prefix = f"image:{review['imageId']}:" if "imageId" in review else ""
        labels.extend(prefix + label for label in review["labels"])
    return {
        "result": {
            "verdict": strongest["verdict"],
            "reason": strongest["reason"],
            "labels": labels,
            "imageReviews": [review for review in reviews if "imageId" in review],
            "avatarChanged": state["item"].avatar_changed,
        }
    }


def build_graph() -> Any:
    graph = StateGraph(State, context_schema=Context)
    graph.add_node("text", text)
    graph.add_node("image", moderate_image, retry_policy=RETRY)
    graph.add_node("merge", merge, defer=True)
    graph.add_conditional_edges(START, dispatch, ["text", "image"])
    graph.add_edge("text", "merge")
    graph.add_edge("image", "merge")
    graph.add_edge("merge", END)
    return graph.compile()
