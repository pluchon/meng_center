from typing import TypedDict

from langgraph.graph import END, START, StateGraph
from langgraph.runtime import Runtime

from app.graphs.moderation import Context
from app.graphs.moderation import build_graph as moderation_graph
from app.prompts.templates import VERSION
from app.runtime.models.retry import MODEL_RETRY
from app.schemas.models import PostInput


class State(TypedDict, total=False):
    post: PostInput
    vector: list[float]
    moderation: dict
    result: dict


def build_graph():
    child = moderation_graph()

    async def moderate(state, runtime: Runtime[Context]):
        value = await child.ainvoke({"item": state["post"]}, context=runtime.context)
        return {"moderation": value}

    async def embed(state, runtime: Runtime[Context]):
        post = state["post"]
        value = f"{post.title}\n{' '.join(post.tags)}\n{post.plain_text[:1500]}"
        return {"vector": (await runtime.context.models.embed([value], "document"))[0]}

    def merge(state, runtime: Runtime[Context]):
        post, child = state["post"], state["moderation"]
        result = {**child["understanding"].model_dump(mode="json"), **child["result"]}
        result.pop("avatarChanged", None)
        settings = runtime.context.models.settings
        result.update(
            postId=post.post_id,
            version=post.version,
            embedding=state["vector"],
            imagesChanged=post.images_changed,
            modelVersion=f"{VERSION}:{settings.judge_model}:{settings.vision_model}:"
            f"{settings.embedding_model}:{settings.embedding_dimensions}:doc-v1",
            analyzeState="FAILED" if child.get("errors") else "SUCCESS",
            moderationState="FAILED" if child.get("errors") else "SUCCESS",
        )
        return {"result": result}

    graph = StateGraph(State, context_schema=Context)
    graph.add_node("moderation", moderate)
    graph.add_node("embed", embed, retry_policy=MODEL_RETRY)
    graph.add_node("merge", merge, defer=True)
    graph.add_edge(START, "moderation")
    graph.add_edge(START, "embed")
    graph.add_edge("moderation", "merge")
    graph.add_edge("embed", "merge")
    graph.add_edge("merge", END)
    return graph.compile()
