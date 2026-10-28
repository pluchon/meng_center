"""评测专用的固定时钟与工具录制结果，不访问网络和业务服务。"""

from datetime import date, timedelta

from app.runtime.context.card import memory_text
from app.runtime.context.segments import text
from app.runtime.tools.registry import TOOLS


def scene(index, clock=None):
    day = date(2026, 10, 24) + timedelta(days=index)
    clock = clock or {0: "早上八点", 10: "晚上七点", 15: "晚上十点", 20: "凌晨两点"}.get(
        index % 30, "下午三点"
    )
    return (
        f"{day.year}年{day.month}月{day.day}日，星期{'一二三四五六日'[day.weekday()]}，用户当地时间{clock}。"
    )


class RecordedTools:
    async def execute(self, call, context):
        name = call["name"]
        TOOLS[name].schema.model_validate(call["args"])
        facts = {
            "search_posts": ["公开帖子《Vue 组件入门》介绍组件拆分和属性传递。"],
            "read_post": ["公开帖子《Vue 组件入门》的正文介绍组件拆分和属性传递。"],
            "my_recent_posts": ["测试用户最近公开发布了一篇学习 Vue 的帖子。"],
            "my_growth": ["测试用户最近有签到和发帖记录。"],
            "recall": [],
        }
        selected = []
        if name == "recall":
            # 录制重排选中前五条候选，不扩展真实工具只能读取本轮候选的边界。
            selected = context["request"].recalled[:5]
            facts[name] = [text(row) for row in selected]
            context["memoryIds"].update(row["id"] for row in selected)
        context["toolUsage"].append(dict(name=name, success=True, recorded=True, elapsedMs=0))
        return dict(
            id=call["id"],
            success=True,
            facts=facts[name],
            speechFacts=[memory_text(selected)] if selected else [],
            sources=[dict(postId=101, title="Vue 组件入门")] if name == "search_posts" else [],
        )
