from __future__ import annotations

import operator
from dataclasses import dataclass, field
from typing import Annotated, Any, TypedDict

import httpx
from langchain_core.callbacks import UsageMetadataCallbackHandler
from langchain_core.messages import HumanMessage, SystemMessage
from langgraph.graph import END, START, StateGraph
from langgraph.runtime import Runtime
from langgraph.types import RetryPolicy, Send
from openai import APIConnectionError, APITimeoutError, RateLimitError

from app.chains.structured import build_chain, structured
from app.clients.models import ModelFactory
from app.prompts.templates import MODERATION, SAFETY, VERSION
from app.schemas.models import Moderation, PostInput, Understanding, Verdict


@dataclass
class Context:
    models: ModelFactory
    callback: UsageMetadataCallbackHandler
    attempts: dict[str, int] = field(default_factory=dict, init=False)


class State(TypedDict, total=False):
    post: PostInput
    text: str
    vector: list[float]
    understanding: Understanding
    image: dict[str, Any]
    reviews: Annotated[list[dict[str, Any]], operator.add]
    errors: Annotated[list[str], operator.add]
    result: dict[str, Any]


def retryable(error: Exception) -> bool:
    if isinstance(error, httpx.HTTPStatusError):
        return error.response.status_code in (429, 500, 502, 503, 504)
    return isinstance(
        error,
        (APIConnectionError, APITimeoutError, RateLimitError, httpx.ConnectError, httpx.TimeoutException),
    )


RETRY = RetryPolicy(max_attempts=3, retry_on=retryable)


def prepare(state: State) -> dict[str, str]:
    post = state["post"]
    return {"text": f"{post.title}\n{' '.join(post.tags)}\n{post.plain_text[:1500]}"}


async def understand(state: State, runtime: Runtime[Context]) -> dict[str, Any]:
    factory = runtime.context.models
    # Graph owns network retries; the node handles final failure as neutral features.
    chain = build_chain(
        getattr(factory, "graph_classifier", factory.classifier),
        Understanding,
        "understand",
        factory.settings.structured_method,
    )
    try:
        async with factory.semaphores["classifier"]:
            result = await chain.ainvoke(
                {"payload": state["post"].model_dump_json(exclude={"image_thumbs"})},
                config={
                    "callbacks": [runtime.context.callback],
                    "run_name": "post-understanding",
                    "tags": ["analysis"],
                    "metadata": {"post_id": state["post"].post_id},
                },
            )
        return {"understanding": Understanding.model_validate(result)}
    except Exception as error:
        attempts = runtime.context.attempts
        attempts["understand"] = attempts.get("understand", 0) + 1
        if retryable(error) and attempts["understand"] < 3:
            raise
        return {
            "understanding": Understanding(topics=[state["post"].category], quality=3, verdict=Verdict.PASS),
            "errors": ["understanding_failed"],
        }


async def embed(state: State, runtime: Runtime[Context]) -> dict[str, list[float]]:
    return {"vector": (await runtime.context.models.embed([state["text"]]))[0]}


async def moderate_image(state: State, runtime: Runtime[Context]) -> dict[str, Any]:
    factory = runtime.context.models
    image = state["image"]
    chain = structured(factory.vision, Moderation, factory.settings.structured_method)
    try:
        async with factory.semaphores["vision"]:
            result = await chain.ainvoke(
                [
                    SystemMessage(SAFETY + MODERATION),
                    HumanMessage(
                        content=[
                            {
                                "type": "image_url",
                                "image_url": {"url": "data:image/jpeg;base64," + image["data"]},
                            }
                        ]
                    ),
                ],
                config={
                    "callbacks": [runtime.context.callback],
                    "run_name": "image-moderation",
                    "tags": ["moderation"],
                    "metadata": {"image_id": image["id"]},
                },
            )
    except Exception as error:
        key = f"image:{image['id']}"
        attempts = runtime.context.attempts
        attempts[key] = attempts.get(key, 0) + 1
        if retryable(error) and attempts[key] < 3:
            raise
        result = Moderation(verdict=Verdict.SUSPECT, labels=["图片待复核"], reason="图片审核暂不可用")
    return {"reviews": [{"imageId": image["id"], **result.model_dump(mode="json")}]}


def dispatch(state: State) -> list[Send]:
    return [Send("understand", state), Send("embed", state)] + [
        Send("image", {"image": img.model_dump()}) for img in state["post"].image_thumbs
    ]


def merge(state: State, runtime: Runtime[Context]) -> dict[str, Any]:
    post = state["post"]
    result = state["understanding"].model_dump(mode="json")
    severity = {"PASS": 0, "SUSPECT": 1, "BLOCK": 2}
    for review in state.get("reviews", []):
        if severity[review["verdict"]] > severity[result["verdict"]]:
            result.update(verdict=review["verdict"], reason=review["reason"])
        result["labels"] += [f"image:{review['imageId']}:{label}" for label in review["labels"]]
    settings = runtime.context.models.settings
    result.update(
        imageReviews=state.get("reviews", []),
        imagesChanged=post.images_changed,
        postId=post.post_id,
        version=post.version,
        embedding=state["vector"],
        modelVersion=f"{VERSION}:{settings.classifier_model}:{settings.vision_model}:"
        f"{settings.embedding_model}:{settings.embedding_dimensions}",
        analyzeState="FAILED" if state.get("errors") else "SUCCESS",
    )
    return {"result": result}


def build_graph() -> Any:
    graph = StateGraph(State, context_schema=Context)
    graph.add_node("prepare", prepare)
    graph.add_node("understand", understand, retry_policy=RETRY)
    graph.add_node("embed", embed, retry_policy=RETRY)
    graph.add_node("image", moderate_image, retry_policy=RETRY)
    graph.add_node("merge", merge, defer=True)
    graph.add_edge(START, "prepare")
    graph.add_conditional_edges("prepare", dispatch, ["understand", "embed", "image"])
    for name in ("understand", "embed", "image"):
        graph.add_edge(name, "merge")
    graph.add_edge("merge", END)
    return graph.compile()
