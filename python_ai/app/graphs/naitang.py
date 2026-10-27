from typing import TypedDict

from langgraph.graph import END, START, StateGraph
from langgraph.runtime import Runtime

from app.runtime.agent.agent import Context
from app.runtime.models.retry import MODEL_RETRY


class State(TypedDict, total=False):
    directive: object
    draft: object
    prejudgment: object
    reply: object
    judgment: object
    result: dict


def approved(review):
    return review is not None and review.safe and not review.unsupported and review.artificial < 7


def build():
    async def agent(state, runtime: Runtime[Context]):
        ctx = runtime.context.data
        return {"directive": await ctx["service"].agent.run(ctx)}

    async def draft(state, runtime: Runtime[Context]):
        ctx = runtime.context.data
        return {"draft": await candidate(ctx, {})}

    async def candidate(ctx, instruction, rewrite=""):
        try:
            return await ctx["service"].speak(ctx["request"], ctx["emotions"], instruction, rewrite)
        except Exception:
            return None

    async def check(ctx, instruction, value):
        if value is None:
            return None
        parsed, sizes = value
        try:
            return await ctx["service"].judge(
                ctx["request"], ctx["emotions"], instruction, parsed, speech_model=sizes.get("speechModel")
            )
        except Exception:
            return None

    async def prejudge(state, runtime: Runtime[Context]):
        ctx = runtime.context.data
        return {"prejudgment": None if ctx["request"].images else await check(ctx, {}, state["draft"])}

    def merge(state):
        return {}

    def route(state, runtime: Runtime[Context]):
        ctx = runtime.context.data
        directive = state["directive"]
        if directive.label in {"CRISIS", "SEXUAL", "JAILBREAK"}:
            return "block"
        if state["draft"] is None or (not state.get("prejudgment") and not directive.image_note):
            return "fallback"
        if directive.facts or directive.mention_memory_id or ctx["toolUsage"] or ctx["request"].images:
            return "respeak"
        return "emit" if approved(state["prejudgment"]) else "respeak"

    async def respeak(state, runtime: Runtime[Context]):
        ctx = runtime.context.data
        directive = state["directive"].model_dump()
        directive["toolResults"] = ctx.get("toolResults", [])
        review = state.get("prejudgment")
        reason = (
            review.reason + "；" + "；".join(review.unsupported) if review and not approved(review) else ""
        )
        return {"reply": await candidate(ctx, directive, reason)}

    async def judge(state, runtime: Runtime[Context]):
        ctx = runtime.context.data
        directive = state["directive"].model_dump()
        directive["toolResults"] = ctx.get("toolResults", [])
        return {"judgment": await check(ctx, directive, state["reply"])}

    def after_judge(state):
        review = state.get("judgment")
        return "fallback" if review is None or not review.safe else "emit"

    def result(state, runtime: Runtime[Context], fixed=None):
        ctx = runtime.context.data
        adopted = fixed is None and "reply" not in state
        value = state.get("reply") or state.get("draft")
        parsed, sizes = value if value else (None, {})
        review = state.get("judgment") or state.get("prejudgment")
        label = state["directive"].label
        return {
            "result": dict(
                label=label,
                bubbles=[fixed] if fixed else parsed.bubbles,
                sticker=parsed.sticker if fixed is None else None,
                sources=list({row["postId"]: row for row in ctx["sources"]}.values())[:5]
                if fixed is None
                else [],
                toolUsage=ctx["toolUsage"],
                billable=label != "CRISIS" if fixed and label != "PASS" else fixed is None,
                promptVersion=ctx["service"].prompt_version(ctx["request"]),
                model="fixed"
                if fixed
                else sizes.get("speechModel", ctx["service"].models.settings.speech_model),
                mentionedMemoryId=review.mentioned_memory
                if review and review.mentioned_memory in ctx["memoryIds"]
                else None,
                metrics=dict(
                    layers=sizes,
                    rewrites=int("reply" in state),
                    speculativeStarted=True,
                    speculativeAdopted=adopted,
                    unsupported=len(review.unsupported) if review else 0,
                    character=review.character if review else 0,
                    artificial=review.artificial if review else 0,
                    rewriteReason=review.reason if review else "",
                ),
            )
        }

    def emit(state, runtime: Runtime[Context]):
        return result(state, runtime)

    def block(state, runtime: Runtime[Context]):
        card = runtime.context.data["request"].card
        key = "crisis" if state["directive"].label == "CRISIS" else "refusal"
        return result(state, runtime, card.get(key, "这个话题先停下来。"))

    def fallback(state, runtime: Runtime[Context]):
        return result(
            state, runtime, runtime.context.data["request"].card.get("fallback", "刚才没说清楚，等一下再说。")
        )

    graph = StateGraph(State, context_schema=Context)
    for name, node in {
        "agent": agent,
        "draft": draft,
        "prejudge": prejudge,
        "respeak": respeak,
        "judge": judge,
        "block": block,
        "emit": emit,
        "fallback": fallback,
    }.items():
        graph.add_node(name, node, retry_policy=None if name == "agent" else MODEL_RETRY)
    graph.add_node("merge", merge, defer=True)
    graph.add_edge(START, "agent")
    graph.add_edge(START, "draft")
    graph.add_edge("draft", "prejudge")
    graph.add_edge("agent", "merge")
    graph.add_edge("prejudge", "merge")
    graph.add_conditional_edges("merge", route)
    graph.add_edge("respeak", "judge")
    graph.add_conditional_edges("judge", after_judge)
    for name in ("emit", "block", "fallback"):
        graph.add_edge(name, END)
    return graph.compile()
