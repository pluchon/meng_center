from __future__ import annotations

import operator
from typing import Annotated, Any, TypedDict

from langgraph.graph import END, START, StateGraph
from langgraph.runtime import Runtime
from langgraph.types import Send

from app.chains.structured import build_chain
from app.graphs.post_analysis import RETRY, Context, moderate_image
from app.schemas.models import Moderation, ModerationInput


class State(TypedDict, total=False):
    item: ModerationInput
    reviews: Annotated[list[dict[str, Any]], operator.add]
    result: dict[str, Any]


async def text(state: State, runtime: Runtime[Context]) -> dict[str, Any]:
    factory = runtime.context.models
    item = state["item"]
    chain = build_chain(factory.classifier, Moderation, "moderate", factory.settings.structured_method)
    async with factory.semaphores["classifier"]:
        review = await chain.ainvoke(
            {"payload": item.model_dump_json(exclude={"image_thumbs", "avatar_thumb"})},
            config={
                "callbacks": [runtime.context.callback],
                "run_name": "text-moderation",
                "tags": ["moderation"],
                "metadata": {"comment_id": item.comment_id, "user_id": item.user_id},
            },
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
