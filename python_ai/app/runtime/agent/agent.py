import asyncio
import logging
from dataclasses import dataclass

from langchain_core.messages import ToolMessage
from langgraph.graph import END, START, StateGraph
from langgraph.runtime import Runtime
from pydantic import ValidationError
from typing_extensions import TypedDict

from app.runtime.agent.directive import Directive, validate
from app.runtime.context.companion import messages
from app.runtime.context.segments import text
from app.runtime.tools.registry import TOOLS

LOG = logging.getLogger(__name__)


class State(TypedDict):
    directive: Directive


@dataclass
class Context:
    data: dict


class Agent:
    def __init__(self, service):
        self.service = service
        declarations = [spec.declaration() for spec in TOOLS.values()]
        declarations.append(
            {
                "type": "function",
                "function": {
                    "name": "respond",
                    "description": "结束审核与场景判断。闲聊只填label和scene；"
                    "有工具或记忆才补facts和可选hint，空字段省略。危机用CRISIS结束。",
                    "parameters": Directive.compact_tool_schema(),
                },
            }
        )
        self.model = service.models.agent.bind_tools(
            declarations, parallel_tool_calls=True, tool_choice="required"
        )
        graph = StateGraph(State, context_schema=Context)
        graph.add_node("decide", self.decide)
        graph.add_edge(START, "decide")
        graph.add_edge("decide", END)
        self.graph = graph.compile()

    async def decide(self, state, runtime: Runtime[Context]):
        context = runtime.context.data
        request = context["request"]
        conversation, _ = messages(request, [], "判断当前输入，最后调用 respond。", speech=False)
        count, tool_ids = 0, set()
        for step in range(3):
            async with self.service.models.semaphores["agent"]:
                result = await self.model.ainvoke(
                    conversation,
                    tool_choice=(
                        {"type": "function", "function": {"name": "respond"}} if step == 2 else "required"
                    ),
                )
            conversation.append(result)
            calls = result.tool_calls
            responses = [call for call in calls if call["name"] == "respond"]
            if responses:
                return {"directive": await self.finish(result, conversation, context, tool_ids)}
            count += len(calls)
            if not calls or count > 4 or step >= 2 or any(call["name"] not in TOOLS for call in calls):
                raise ValueError("主 agent 超过执行边界或未给出说话指令")
            # 工具结果会改变说话依据，立即取消不再适用的预生成。
            speculative = context.get("speculative")
            if speculative and not speculative.done():
                speculative.cancel()
                await asyncio.gather(speculative, return_exceptions=True)
            # 未经审核不执行工具；本次工具调用本身需要明确的非危机上下文。
            async with asyncio.TaskGroup() as group:
                pending = [group.create_task(self.service.executor.execute(call, context)) for call in calls]
            results = [task.result() for task in pending]
            for call, value in zip(calls, results, strict=True):
                tool_ids.add(call["id"])
                context.setdefault("toolResults", []).append(
                    dict(
                        id=call["id"],
                        name=call["name"],
                        success=value.get("success", True),
                        facts=value.get("facts", []),
                        speechFacts=value.get("speechFacts", []),
                    )
                )
                conversation.append(ToolMessage(text(value), tool_call_id=call["id"]))
        raise ValueError("主 agent 未结束")

    async def finish(self, result, conversation, context, tool_ids):
        request = context["request"]
        for attempt in range(2):
            calls = result.tool_calls
            try:
                if len(calls) != 1 or calls[0]["name"] != "respond":
                    raise ValueError("respond 必须独立结束")
                directive = Directive.model_validate(calls[0]["args"])
                return validate(
                    directive,
                    tool_ids,
                    context["memoryIds"],
                    bool(request.content or request.history or request.summary),
                )
            except ValueError as error:
                issues = []
                if isinstance(error, ValidationError):
                    issues = [
                        {"field": ".".join(map(str, issue["loc"])), "expected": issue["msg"]}
                        for issue in error.errors(
                            include_input=False, include_context=False, include_url=False
                        )
                    ]
                LOG.warning(
                    "agent_directive_rejected error_type=%s fields=%s",
                    type(error).__name__,
                    ",".join(issue["field"] for issue in issues),
                )
                if attempt or not calls:
                    raise
                context["directiveRetried"] = True
                sources = ["history"] + [f"tool:{value}" for value in sorted(tool_ids)]
                sources += [f"memory:{value}" for value in sorted(context["memoryIds"])]
                feedback = "指令参数或出处不合法。本轮只重试respond，不执行工具。可用出处：" + text(sources)
                if issues:
                    feedback += "；参数问题：" + text(issues)
                feedback += "；history包括本轮原始输入、场景和已有对话，不能编造过往。"
                for call in calls:
                    conversation.append(ToolMessage(feedback, tool_call_id=call["id"]))
                async with self.service.models.semaphores["agent"]:
                    result = await self.model.ainvoke(
                        conversation, tool_choice={"type": "function", "function": {"name": "respond"}}
                    )
                conversation.append(result)
        raise ValueError("说话指令未通过校验")

    async def run(self, context):
        last = None
        for _ in range(2):
            try:
                return (await self.graph.ainvoke({}, context=Context(context)))["directive"]
            except Exception as error:
                # 工具执行后不重放副作用；没有执行工具时才能重试整个决定。
                last = error
                reasons = {
                    "主 agent 超过执行边界或未给出说话指令": "tool_boundary_or_missing_response",
                    "说话指令引用了本轮未提供的出处": "unknown_source",
                    "说话指令引用了未知记忆": "unknown_memory",
                    "respond 必须独立结束": "mixed_response",
                }
                LOG.warning("agent_decision_failed reason=%s", reasons.get(str(error), "invalid_result"))
                if context["toolUsage"] or context.get("directiveRetried"):
                    break
        raise ValueError("主 agent 未提供合法结果") from last
