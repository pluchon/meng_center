from collections.abc import Callable
from dataclasses import dataclass
from typing import Any

from langchain.tools import ToolRuntime, tool
from pydantic import BaseModel, Field

from app.runtime.tools.facts import clean_result


class Empty(BaseModel):
    pass


class Query(BaseModel):
    query: str = Field(min_length=1, max_length=300)


class Post(BaseModel):
    post_id: int = Field(gt=0)


class WebQuery(BaseModel):
    query: str = Field(min_length=1, max_length=64)


@dataclass(frozen=True)
class ToolSpec:
    name: str
    description: str
    schema: type[BaseModel]
    progress: str
    timeout: float = 8
    cache_seconds: int = 30
    cleaner: Callable[[dict], dict] = clean_result


TOOLS = {
    s.name: s
    for s in (
        ToolSpec(
            "web_search",
            "只在当前用户明确询问需要现查的站外事实时搜索，如新闻、作品资料、播出、发售或活动日期；"
            "闲聊、常识、角色自身及社区帖子不搜索。查询仅含当前输入中的问题，最多64字；"
            "不带昵称、历史、记忆、所在地或图片信息，用户本轮明确提供的城市或作品名除外",
            WebQuery,
            "在查网页…",
            timeout=8,
            cache_seconds=600,
        ),
        ToolSpec(
            "recall",
            "仅在用户明确询问以前聊过的事且已有资料不足时召回记忆；"
            "只重排本轮额外候选，不读取当前对话；指代不明时先澄清，不用检索猜测对象；"
            "讲述过去的日常、表达情绪或询问角色设定时不调用",
            Query,
            "在想以前聊过的事…",
            cache_seconds=60,
        ),
        ToolSpec(
            "search_posts",
            "仅在用户明确要求查找或推荐站内帖子时检索；不因提到某个话题而检索，不为闲聊补充材料",
            Query,
            "在翻帖子…",
            timeout=6,
        ),
        ToolSpec(
            "read_post",
            "读取用户指定或本轮检索结果中的公开帖子；仅用于完成用户明确提出的帖子阅读请求",
            Post,
            "在看帖子…",
            timeout=3,
            cache_seconds=60,
        ),
        ToolSpec(
            "my_recent_posts",
            "仅在用户明确要求查看自己最近公开的帖子时读取",
            Empty,
            "在看你最近的帖子…",
            timeout=3,
        ),
        ToolSpec(
            "my_growth",
            "仅在用户明确要求查看自己的签到、经验、等级或成长记录时读取",
            Empty,
            "在看你的成长记录…",
            timeout=3,
        ),
    )
}


def declare(spec):
    async def execute(runtime: ToolRuntime, **kwargs):
        context = runtime.context.data
        result = await context["service"].executor.execute(
            {"id": runtime.tool_call_id, "name": spec.name, "args": kwargs}, context
        )
        context.setdefault("toolResults", []).append({"name": spec.name, **result})
        return {**result, "source": "tool:" + runtime.tool_call_id}

    if spec.schema is Empty:
        # 从函数推导注入参数，避免空模型触发无参数快捷路径而丢失运行时。
        async def execute_empty(runtime: ToolRuntime[Any]):
            return await execute(runtime)

        return tool(spec.name, description=spec.description)(execute_empty)
    return tool(spec.name, description=spec.description, args_schema=spec.schema)(execute)
