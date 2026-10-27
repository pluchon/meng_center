import asyncio
import logging
import time

from langgraph.config import get_stream_writer

from app.runtime.context.card import memory_text
from app.runtime.context.segments import text
from app.runtime.tools.facts import ToolFacts, clean
from app.runtime.tools.registry import TOOLS

LOG = logging.getLogger(__name__)


class Executor:
    def __init__(self, service):
        self.service = service

    async def execute(self, call, context):
        ident, name = call["id"], call["name"]
        start, cached = time.monotonic(), False
        spec = TOOLS.get(name)
        try:
            if spec is None:
                raise ValueError("未知工具")
            args = spec.schema.model_validate(call["args"]).model_dump()
            request = context["request"]
            if not request.message_allowed:
                return {"id": ident, "success": False, "facts": ["今天聊天的次数已经用完了"], "sources": []}
            get_stream_writer()({"event": "status", "data": {"text": spec.progress}})
            async with asyncio.timeout(spec.timeout):
                result, cached = await self.invoke(name, args, context)
            context["toolUsage"].append(
                dict(
                    name=name,
                    success=True,
                    cached=cached,
                    elapsedMs=round((time.monotonic() - start) * 1000),
                )
            )
            return {"id": ident, **result, "success": True}
        except Exception as error:
            LOG.warning(
                "tool_failed tool=%s error_type=%s cause_type=%s",
                name,
                type(error).__name__,
                type(error.__cause__).__name__ if error.__cause__ else "",
            )
            context["toolUsage"].append(
                dict(
                    name=name,
                    success=False,
                    cached=False,
                    elapsedMs=round((time.monotonic() - start) * 1000),
                )
            )
            return {
                "id": ident,
                "success": False,
                "facts": ["暂时查不到，结果未知"],
                "sources": [],
            }

    async def invoke(self, name, args, context):
        service, request = self.service, context["request"]
        if name == "recall":
            rows = request.recalled
            ranked = await service.models.rerank.rank(
                args["query"], [text(row) for row in rows], scene="recall"
            )
            selected = [rows[i] for i in ranked["indexes"]]
            context["memoryIds"].update(row["id"] for row in selected if isinstance(row.get("id"), int))
            return {
                "facts": [text(row) for row in selected],
                "speechFacts": [memory_text(selected)] if selected else [],
            }, False
        if name == "search_posts":
            vector = (await service.models.embed([args["query"]], "query"))[0]
            rows = await service.internal(
                "meng-content",
                "/content/internal/posts/vector-search",
                {"vector": vector, "size": 20},
                post=True,
            )
            ranked = await service.models.rerank.rank(
                args["query"], [text(row) for row in rows], scene="search"
            )
            rows = [rows[i] for i in ranked["indexes"]]
        elif name == "read_post":
            rows = [await service.internal("meng-content", f"/content/internal/posts/{args['post_id']}")]
        elif name == "my_recent_posts":
            rows = await service.internal(
                "meng-content", f"/content/internal/users/{request.user_id}/recent-posts", {"size": 5}
            )
        else:
            rows = [
                await service.internal("meng-growth", f"/growth/internal/users/{request.user_id}/summary")
            ]
        # 外部正文先提炼；原始资料不进入主 agent 与嘴。
        result = await service.structured(
            ToolFacts,
            "从资料中提炼可直接回答当前输入的短事实，每条给出资料下标。",
            {"input": request.content, "rows": rows},
        )
        if result is None or any(f.index >= len(rows) for f in result.facts):
            raise ValueError("工具事实缺少依据")
        sources = [
            dict(postId=row["id"], title=row["title"]) for row in rows if "id" in row and "title" in row
        ]
        context["sources"].extend(sources)
        return {"facts": [clean(f.content) for f in result.facts], "sources": sources}, False
