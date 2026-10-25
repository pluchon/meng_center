from dataclasses import dataclass
from typing import Literal

from pydantic import BaseModel, Field


class Empty(BaseModel):
    pass


class Query(BaseModel):
    query: str = Field(min_length=1, max_length=300)


class Search(Query):
    topic: Literal["general", "news"] = "general"
    time_range: Literal["day", "week", "month", "year"] | None = None


class Link(BaseModel):
    url: str = Field(max_length=2000)


class Post(BaseModel):
    post_id: int = Field(gt=0)


class Draw(BaseModel):
    instruction: str = Field(min_length=1, max_length=2000)
    spontaneous: bool = Field(default=False, description="用户没有要求画图、由角色主动决定时为true")


class Instruction(BaseModel):
    instruction: str = Field(min_length=1, max_length=2000)


@dataclass(frozen=True)
class ToolSpec:
    name: str
    description: str
    schema: type[BaseModel]
    progress: str
    ack: bool = False
    quota: str | None = None
    timeout: float = 8
    cache_seconds: int = 0

    def declaration(self):
        return {
            "type": "function",
            "function": {
                "name": self.name,
                "description": self.description,
                "parameters": self.schema.model_json_schema(),
            },
        }


TOOLS = {
    s.name: s
    for s in (
        ToolSpec(
            "recall",
            "确认资料缺失的过往事实；普通闲聊、情绪倾诉和角色设定问题不调用",
            Query,
            "在想以前聊过的事…",
        ),
        ToolSpec(
            "web_search", "搜索公共事实，时间查询带当前年份", Search, "在网上查…", True, "web", 12, 21600
        ),
        ToolSpec("read_link", "阅读用户给出的网页链接", Link, "在看你发的链接…", True, "web", 12, 21600),
        ToolSpec("search_images", "找最多两张安全且相关的网络图片", Query, "在找图…", True, "webImage", 22),
        ToolSpec(
            "draw_picture",
            "登记后台画图，尚未完成，画好后另行通知",
            Draw,
            "准备画画…",
            True,
            "image",
            1,
        ),
        ToolSpec("make_toy", "制作单文件HTML小玩意", Instruction, "在做小玩意…", True, "toy", 60),
        ToolSpec("search_posts", "按含义检索公开帖子", Query, "在翻帖子…", timeout=6),
        ToolSpec("read_post", "读取一篇公开帖子的正文", Post, "在看帖子…", timeout=3),
        ToolSpec("my_recent_posts", "读取当前用户最近公开的帖子", Empty, "在看你最近的帖子…", timeout=3),
        ToolSpec("my_growth", "读取当前用户成长数据", Empty, "在看你的成长记录…", timeout=3),
    )
}
