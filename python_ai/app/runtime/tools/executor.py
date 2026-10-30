import asyncio
import hashlib
import json
import logging
import time

from langgraph.config import get_stream_writer

from app.runtime.tools.registry import TOOLS

LOG = logging.getLogger(__name__)


class Executor:
    def __init__(self, service):
        self.service = service
        self.cache_seconds = getattr(service.models.settings, "tool_cache_seconds", {})
        if set(self.cache_seconds) - TOOLS.keys():
            raise ValueError("工具缓存配置包含未知工具")

    def cache_key(self, name, args, request):
        settings = self.service.models.settings
        value = dict(
            tool=name,
            arguments=args,
            input=request.content,
            candidates=request.recalled if name == "recall" else [],
            version=self.service.prompt_version(request),
            model=getattr(settings, "search_model" if name == "web_search" else "judge_model", ""),
            modelOptions=getattr(settings, "model_overrides", {}),
            taskOptions={
                name: options.model_dump(exclude_none=True) if hasattr(options, "model_dump") else options
                for name, options in getattr(settings, "task_overrides", {}).items()
            },
        )
        digest = hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False).encode()).hexdigest()
        return f"meng:companion:tool:{request.user_id}:{digest}"

    async def cached(self, key, cleaner):
        try:
            async with asyncio.timeout(0.1):
                raw = await self.service.models.redis.get(key)
            if isinstance(raw, (str, bytes)) and len(raw) <= 16000:
                return cleaner(json.loads(raw))
        except Exception as error:
            LOG.warning("tool_cache_read_skipped error_type=%s", type(error).__name__)
        return None

    async def save(self, key, value, seconds):
        try:
            async with asyncio.timeout(0.1):
                await self.service.models.redis.set(key, json.dumps(value, ensure_ascii=False), ex=seconds)
        except Exception as error:
            LOG.warning("tool_cache_write_skipped error_type=%s", type(error).__name__)

    async def execute(self, call, context):
        ident, name = call["id"], call["name"]
        start, cached = time.monotonic(), False
        if name == "web_search":
            context["searchCalls"] = 0
        spec = TOOLS.get(name)
        try:
            if spec is None:
                raise ValueError("未知工具")
            args = spec.schema.model_validate(call["args"]).model_dump()
            request = context["request"]
            if name == "web_search" and not request.web_search_remaining:
                raise ValueError("今天搜索次数已经用完了")
            if not request.message_allowed:
                return {"id": ident, "success": False, "facts": ["今天聊天的次数已经用完了"], "sources": []}
            get_stream_writer()({"event": "status", "data": {"text": spec.progress}})
            seconds = self.cache_seconds.get(name, spec.cache_seconds)
            key = self.cache_key(name, args, request)
            result = await self.cached(key, spec.cleaner) if seconds else None
            cached = result is not None
            if not cached:
                async with asyncio.timeout(spec.timeout):
                    result = spec.cleaner(await self.invoke(name, args, context))
            available = {row["id"] for row in request.recalled} | context["memoryIds"]
            if not set(result["memoryIds"]).issubset(available):
                raise ValueError("工具记忆出处不属于本轮候选")
            if not cached and seconds:
                await self.save(key, result, seconds)
            context["memoryIds"].update(result["memoryIds"])
            context["sources"].extend(result["sources"])
            result = {key: value for key, value in result.items() if key != "memoryIds"}
            context["toolUsage"].append(
                dict(
                    name=name,
                    success=True,
                    cached=cached,
                    elapsedMs=round((time.monotonic() - start) * 1000),
                    **({"searchCalls": 0 if cached else context.get("searchCalls", 0)}
                       if name == "web_search" else {}),
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
                    **({"searchCalls": 0} if name == "web_search" else {}),
                )
            )
            return {
                "id": ident,
                "success": False,
                "facts": ["暂时查不到，结果未知"],
                "sources": [],
            }

    async def invoke(self, name, args, context):
        from app.runtime.tools import growth, posts, recall, web

        if name == "recall":
            return await recall.execute(self.service, args, context)
        if name == "web_search":
            return await web.execute(self.service, args, context)
        if name == "my_growth":
            return await growth.execute(self.service, args, context)
        return await posts.execute(self.service, name, args, context)
