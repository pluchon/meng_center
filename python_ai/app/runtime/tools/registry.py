from dataclasses import dataclass

from pydantic import BaseModel, Field


class Empty(BaseModel):
    pass


class Query(BaseModel):
    query: str = Field(min_length=1, max_length=300)


class Post(BaseModel):
    post_id: int = Field(gt=0)


@dataclass(frozen=True)
class ToolSpec:
    name: str
    description: str
    schema: type[BaseModel]
    progress: str
    timeout: float = 8

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
        ToolSpec("search_posts", "按含义检索公开帖子", Query, "在翻帖子…", timeout=6),
        ToolSpec("read_post", "读取一篇公开帖子的正文", Post, "在看帖子…", timeout=3),
        ToolSpec("my_recent_posts", "读取当前用户最近公开的帖子", Empty, "在看你最近的帖子…", timeout=3),
        ToolSpec("my_growth", "读取当前用户成长数据", Empty, "在看你的成长记录…", timeout=3),
    )
}
