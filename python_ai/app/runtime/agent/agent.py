from contextvars import ContextVar
from dataclasses import dataclass

from langchain.agents import create_agent
from langchain.agents.middleware import (
    ModelCallLimitMiddleware,
    ModelRetryMiddleware,
    ToolCallLimitMiddleware,
    wrap_model_call,
    wrap_tool_call,
)
from langchain.agents.structured_output import ToolStrategy
from langchain_core.messages import ToolMessage
from pydantic import model_validator

from app.runtime.agent.directive import Directive, validate
from app.runtime.context.companion import messages
from app.runtime.models.retry import transient
from app.runtime.tools.registry import TOOLS, declare

ACTIVE: ContextVar[dict | None] = ContextVar("agent_context", default=None)


@dataclass
class Context:
    data: dict


class Response(Directive):
    """完成当前输入的审核并结束决策。普通分享、情绪和含糊追问立即使用本工具，
    不先检索；仅填写label和scene。只有当前用户明确提出查询请求才先使用业务工具。
    """

    @model_validator(mode="after")
    def check_sources(self):
        context = ACTIVE.get()
        if context is not None:
            if context["request"].images and not self.image_note:
                raise ValueError("用户带图时必须填写imageNote")
            validate(self, {row["id"] for row in context.get("toolResults", [])}, context["memoryIds"], True)
        return self


def repair(error):
    context = ACTIVE.get()
    issue = {"type": type(error).__name__}
    source = getattr(error, "source", error)
    validation = getattr(source, "__cause__", None)
    if hasattr(validation, "errors"):
        issue["fields"] = validation.errors(include_input=False, include_context=False, include_url=False)
    context.setdefault("directiveIssues", []).append(issue)
    if context.get("directiveRetried"):
        raise ValueError("说话指令重试后仍不合法") from error
    context["directiveRetried"] = True
    sources = ["history"] + ["tool:" + row["id"] for row in context.get("toolResults", [])]
    sources += [f"memory:{ident}" for ident in sorted(context["memoryIds"])]
    fields = "；".join(
        ".".join(str(part) for part in row["loc"]) + "：" + row["msg"]
        for row in issue.get("fields", [])[:3]
    )
    return (
        "只修正说话指令，不再执行工具。"
        + ("校验错误：" + fields + "。" if fields else "")
        + "hint最多20字，可省略。可用出处：" + "、".join(sources)
    )


@wrap_model_call
async def bound_call(request, handler):
    context = request.runtime.context.data
    # 参数校验重试和最后一次模型调用只允许输出结构化指令。
    count = request.state.get("run_model_call_count", 0)
    if context.get("directiveRetried") or count >= 2:
        request = request.override(tools=[])
    else:
        unavailable = set()
        if not context["request"].recalled:
            unavailable.add("recall")
        if (not context["request"].web_search_remaining
                or request.state.get("run_tool_call_count", {}).get("web_search", 0)):
            unavailable.add("web_search")
        request = request.override(tools=[tool for tool in request.tools if tool.name not in unavailable])
    async with context["service"].models.semaphores["agent"]:
        return await handler(request)


@wrap_tool_call
async def available_memory(request, handler):
    context = request.runtime.context.data
    call = request.tool_call
    missing_memory = call["name"] == "recall" and not context["request"].recalled
    missing_search = call["name"] == "web_search" and not context["request"].web_search_remaining
    if missing_memory or missing_search:
        # 隐藏工具不能代替执行校验；拒绝记录仍参与用量统计和评测。
        context["toolUsage"].append(
            dict(name=call["name"], success=False, rejected=True, cached=False, elapsedMs=0,
                 **({"searchCalls": 0} if missing_search else {}))
        )
        return ToolMessage(
            content=("今天搜索次数已经用完了，聊天照常，不再搜索。" if missing_search else
                     "本轮没有可召回的记忆候选。已有对话可直接阅读；指代不明时询问，不再检索。"),
            name=call["name"],
            tool_call_id=call["id"],
            status="error",
        )
    return await handler(request)


class Agent:
    def __init__(self, service):
        self.graph = create_agent(
            service.models.agent,
            tools=[declare(spec) for spec in TOOLS.values()],
            response_format=ToolStrategy(Response, handle_errors=repair),
            context_schema=Context,
            middleware=[
                ModelCallLimitMiddleware(run_limit=3, exit_behavior="error"),
                ToolCallLimitMiddleware(run_limit=4, exit_behavior="error"),
                # 拦截重复搜索后仍可依据已有结果完成指令，不能让限次中断整轮对话。
                ToolCallLimitMiddleware(tool_name="web_search", run_limit=1, exit_behavior="continue"),
                bound_call,
                available_memory,
                ModelRetryMiddleware(max_retries=1, retry_on=transient, on_failure="error"),
            ],
        )

    async def run(self, context):
        token = ACTIVE.set(context)
        try:
            conversation, sizes = messages(context["request"], [], speech=False)
            result = await self.graph.ainvoke(
                {"messages": conversation},
                context=Context(context),
                config={
                    "callbacks": context.get("callbacks", []),
                    "metadata": {"contextSegments": sizes},
                },
            )
            if "structured_response" not in result:
                raise ValueError("主agent缺少说话指令")
            return result["structured_response"]
        finally:
            ACTIVE.reset(token)
