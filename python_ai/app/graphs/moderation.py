from __future__ import annotations

import operator
from dataclasses import dataclass
from typing import Annotated, Any, TypedDict

from langchain_core.messages import HumanMessage, SystemMessage
from langgraph.graph import END, START, StateGraph
from langgraph.runtime import Runtime
from langgraph.types import Send

from app.runtime.models.retry import MODEL_RETRY
from app.runtime.safety.policy import POLICY
from app.runtime.tasks.registry import payload
from app.runtime.tasks.structured import REVIEW_UNAVAILABLE
from app.schemas.models import Moderation, ModerationInput, PostInput, Understanding, Verdict


@dataclass
class Context:
    models: Any
    callback: Any


class State(TypedDict, total=False):
    item: PostInput | ModerationInput
    reviews: Annotated[list[dict], operator.add]
    errors: Annotated[list[str], operator.add]
    understanding: Understanding
    result: dict


async def review_text(factory, value, callback, task="moderate_text"):
    async with factory.semaphores["judge"]:
        return await factory.task_chains[task].ainvoke(
            {"payload": value}, config={"callbacks": [callback] if callback else []}
        )


async def review_image(factory, image, callback):
    async with factory.semaphores["vision"]:
        return await factory.task_runtime.output("moderate_image").ainvoke(
            [
                SystemMessage(POLICY + "\n审核这张图片。"),
                HumanMessage(
                    [{"type": "image_url", "image_url": {"url": "data:image/jpeg;base64," + image["data"]}}]
                ),
            ],
            config={"callbacks": [callback] if callback else []},
        )


async def text(state, runtime: Runtime[Context]):
    item, factory = state["item"], runtime.context.models
    is_post = isinstance(item, PostInput)
    try:
        review = await review_text(
            factory,
            payload(item, {"image_thumbs", "avatar_thumb"}),
            runtime.context.callback,
            "understand" if is_post else "moderate_text",
        )
        review = (Understanding if is_post else Moderation).model_validate(review)
        if is_post:
            from app.runtime.tasks.tags import normalize

            review.topics = normalize(review.topics, item.tag_candidates)
        return {"reviews": [review.model_dump(mode="json")], **({"understanding": review} if is_post else {})}
    except Exception:
        if not is_post:
            raise
        review = Understanding(topics=[item.category], quality=3, verdict=Verdict.PASS)
        return {
            "understanding": review,
            "reviews": [review.model_dump(mode="json")],
            "errors": ["understanding_failed"],
        }


async def image(state, runtime: Runtime[Context]):
    row = state["image"]
    review = await review_image(runtime.context.models, row, runtime.context.callback)
    return {"reviews": [{"imageId": row["id"], **review.model_dump(mode="json")}]}


def dispatch(state):
    item = state["item"]
    images = list(item.image_thumbs)
    if isinstance(item, ModerationInput) and item.avatar_thumb:
        images.append(item.avatar_thumb)
    return [Send("text", state)] + [Send("image", {"image": row.model_dump()}) for row in images]


def merged(reviews):
    severity = {"PASS": 0, "SUSPECT": 1, "BLOCK": 2}
    strongest = max(reviews, key=lambda review: severity[review["verdict"]])
    return dict(
        verdict=strongest["verdict"],
        reason=strongest["reason"],
        labels=[
            (f"image:{r['imageId']}:" if "imageId" in r else "") + label
            for r in reviews
            for label in r["labels"]
        ],
        imageReviews=[r for r in reviews if "imageId" in r],
    )


def merge(state):
    result = merged(state["reviews"])
    result["avatarChanged"] = getattr(state["item"], "avatar_changed", False)
    return {"result": result}


async def confirm(state, runtime: Runtime[Context]):
    factory, item = runtime.context.models, state["item"]
    content = [{"type": "text", "text": payload(item, {"image_thumbs", "avatar_thumb"})}]
    images = list(item.image_thumbs)
    if isinstance(item, ModerationInput) and item.avatar_thumb:
        images.append(item.avatar_thumb)
    content.extend(
        {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64," + r.data}} for r in images
    )
    reason = await confirm_content(factory, content, runtime.context.callback)
    if not reason:
        return {}
    result = {**state["result"], "verdict": "SUSPECT", "reason": reason}
    # Java复用逐图结果时也必须保留复核后的结论。
    result["imageReviews"] = [
        {**row, "verdict": "SUSPECT", "reason": reason} if row["verdict"] == "BLOCK" else row
        for row in result["imageReviews"]
    ]
    return {"result": result}


async def confirm_content(factory, content, callback):
    try:
        async with factory.semaphores["review"]:
            checked = await factory.task_runtime.output("moderate_confirm").ainvoke(
                [SystemMessage(POLICY + "\n独立审核目标内容，语境只作参考。"), HumanMessage(content)],
                config={"callbacks": [callback] if callback else []},
            )
        if checked is None:
            raise ValueError("缺少复核结果")
        if checked.verdict == Verdict.BLOCK:
            return ""
        return REVIEW_UNAVAILABLE if checked.reason == REVIEW_UNAVAILABLE else "初审与复核不一致"
    except Exception:
        return REVIEW_UNAVAILABLE


def build_graph():
    graph = StateGraph(State, context_schema=Context)
    graph.add_node("text", text, retry_policy=MODEL_RETRY)
    graph.add_node("image", image, retry_policy=MODEL_RETRY)
    graph.add_node("merge", merge, defer=True)
    graph.add_node("confirm", confirm, retry_policy=MODEL_RETRY)
    graph.add_conditional_edges(START, dispatch, ["text", "image"])
    graph.add_edge("text", "merge")
    graph.add_edge("image", "merge")
    graph.add_conditional_edges("merge", lambda s: "confirm" if s["result"]["verdict"] == "BLOCK" else END)
    graph.add_edge("confirm", END)
    return graph.compile()
