import asyncio
import logging
import time

from langgraph.config import get_stream_writer

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
        from app.runtime.tools import growth, posts, recall

        if name == "recall":
            return await recall.execute(self.service, args, context), False
        if name == "my_growth":
            return await growth.execute(self.service, args, context), False
        return await posts.execute(self.service, name, args, context), False
