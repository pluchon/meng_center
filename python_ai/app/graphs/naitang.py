from typing import TypedDict

from langchain_core.runnables import RunnableLambda
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


def recoverable(name, node, field):
    # 子图先完成原生RetryPolicy，之后才转为未生成/未审核；不重放主agent或工具。
    graph = StateGraph(State, context_schema=Context)
    graph.add_node(name, node, retry_policy=MODEL_RETRY)
    graph.add_edge(START, name)
    graph.add_edge(name, END)
    runner = graph.compile().with_fallbacks([
        RunnableLambda(lambda _state, **_kwargs: {field: None})
    ])

    async def execute(state, runtime: Runtime[Context]):
        result = await runner.ainvoke(state, context=runtime.context)
        return {field: result.get(field)}

    return execute


def build():
    async def agent(state, runtime: Runtime[Context]):
        ctx = runtime.context.data
        return {"directive": await ctx["service"].agent.run(ctx)}

    async def draft(state, runtime: Runtime[Context]):
        ctx = runtime.context.data
        return {"draft": await candidate(ctx, {})}

    async def candidate(ctx, instruction, rewrite=""):
        return await ctx["service"].speak(ctx["request"], ctx["emotions"], instruction, rewrite)

    async def check(ctx, instruction, value):
        if value is None:
            return None
        parsed, sizes = value
        return await ctx["service"].judge(
            ctx["request"], ctx["emotions"], instruction, parsed, speech_model=sizes.get("speechModel")
        )

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
        # 草稿或预审失败不等于不安全：照常重说并经过打分，打分仍失败才用兜底台词。
        if directive.mention_memory_id or ctx["toolUsage"] or ctx["request"].images:
            return "respeak"
        return "emit" if approved(state["prejudgment"]) else "respeak"

    async def respeak(state, runtime: Runtime[Context]):
        ctx = runtime.context.data
        directive = state["directive"].model_dump()
        directive["toolResults"] = ctx.get("toolResults", [])
        review = state.get("prejudgment")
        reason = ""
        if review and not approved(review):
            reason = "输出检查未通过，根据用户原话重新回应，不复述检查意见。"
            if review.reason:
                reason += "检查意见：" + review.reason
            if review.unsupported:
                reason += "以下说法没有依据，必须删除，不得换成同义句保留：" + "；".join(review.unsupported)
        return {"reply": await candidate(ctx, directive, reason)}

    async def judge(state, runtime: Runtime[Context]):
        ctx = runtime.context.data
        directive = state["directive"].model_dump()
        directive["toolResults"] = ctx.get("toolResults", [])
        return {"judgment": await check(ctx, directive, state["reply"])}

    def after_judge(state):
        review = state.get("judgment")
        return "fallback" if review is None or not review.safe else "emit"

    def result(state, runtime: Runtime[Context], fixed=None, *, billable=True):
        ctx = runtime.context.data
        adopted = fixed is None and "reply" not in state
        value = state.get("reply") or state.get("draft")
        parsed, sizes = value if value else (None, {})
        sizes = {**sizes, "memoryIds": sorted(ctx["memoryIds"]) if fixed is None else []}
        review = state.get("judgment") or state.get("prejudgment")
        label = state["directive"].label
        return {
            "result": dict(
                label=label,
                bubbles=[fixed] if fixed else parsed.bubbles,
                sticker=parsed.sticker if fixed is None else None,
                sources=list({(row.get("type", "POST"), row.get("url", row.get("postId"))): row
                              for row in ctx["sources"]}.values())[:5]
                if fixed is None
                else [],
                toolUsage=ctx["toolUsage"],
                billable=billable,
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
        return result(
            state,
            runtime,
            card.get(key, "这个话题先停下来。"),
            billable=state["directive"].label != "CRISIS",
        )

    def fallback(state, runtime: Runtime[Context]):
        return result(
            state,
            runtime,
            runtime.context.data["request"].card.get("fallback", "刚才没说清楚，等一下再说。"),
            billable=False,
        )

    # 草稿与预审是同一条分支，放进子图作为一个节点，和 agent 在同一超步里并行；
    # 拆成父图里的两个节点时，预审要等 agent 所在的超步结束才开始，联网轮次会白等。
    speculation = StateGraph(State, context_schema=Context)
    speculation.add_node("draft", recoverable("draft", draft, "draft"))
    speculation.add_node("prejudge", recoverable("prejudge", prejudge, "prejudgment"))
    speculation.add_edge(START, "draft")
    speculation.add_edge("draft", "prejudge")
    speculation.add_edge("prejudge", END)

    graph = StateGraph(State, context_schema=Context)
    for name, node in {
        "agent": agent,
        "speculate": speculation.compile(),
        "respeak": respeak,
        "judge": judge,
        "block": block,
        "emit": emit,
        "fallback": fallback,
    }.items():
        field = {"respeak": "reply", "judge": "judgment"}.get(name)
        graph.add_node(name, recoverable(name, node, field) if field else node)
    graph.add_node("merge", merge, defer=True)
    graph.add_edge(START, "agent")
    graph.add_edge(START, "speculate")
    graph.add_edge("agent", "merge")
    graph.add_edge("speculate", "merge")
    graph.add_conditional_edges("merge", route)
    graph.add_edge("respeak", "judge")
    graph.add_conditional_edges("judge", after_judge)
    for name in ("emit", "block", "fallback"):
        graph.add_edge(name, END)
    return graph.compile()
