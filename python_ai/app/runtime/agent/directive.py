from typing import Literal

from pydantic import Field

from app.schemas.models import Schema


class Fact(Schema):
    content: str = Field(min_length=1, max_length=600, description="资料直接支持的短事实，保留原意")
    source: str = Field(max_length=80, description="只能填history、tool:<工具调用id>或memory:<数字记忆编号>")


class Directive(Schema):
    label: Literal["PASS", "SEXUAL", "JAILBREAK", "ROMANCE_PUSH", "CRISIS"]
    scene: Literal["CHAT", "PRAISE", "WORK", "COMFORT", "REST", "GOODBYE", "GREETING"] = "CHAT"
    hint: str = Field(default="", max_length=20, description="可省略；工具、回忆或安全处理的方向，不写台词")
    facts: list[Fact] = Field(default_factory=list, max_length=20)
    support_needed: bool = False
    identity_question: bool = False
    mention_memory_id: int | None = Field(default=None, description="本轮可用的数字记忆编号；没有时填null")


def validate(directive, tool_ids, memory_ids, has_history):
    available = {f"tool:{value}" for value in tool_ids} | {f"memory:{value}" for value in memory_ids}
    if has_history:
        available.add("history")
    if any(fact.source not in available for fact in directive.facts):
        raise ValueError("说话指令引用了本轮未提供的出处")
    if directive.mention_memory_id is not None and directive.mention_memory_id not in memory_ids:
        raise ValueError("说话指令引用了未知记忆")
    # 闲聊只传递审核与场景，不替角色概括用户的话。
    if not tool_ids and directive.mention_memory_id is None and not any(
        fact.source.startswith("memory:") for fact in directive.facts
    ):
        directive.facts = []
        if directive.label == "PASS":
            directive.hint = ""
    return directive
