import asyncio
import logging
import time

from app.runtime.context.card import memory_text
from app.runtime.context.segments import text
from app.runtime.tools import images, toy, web
from app.runtime.tools.registry import TOOLS

LOG = logging.getLogger(__name__)


class Executor:
    def __init__(self, service):
        self.service = service

    async def execute(self, call, context):
        ident, name = call["id"], call["name"]
        start, cached = time.monotonic(), False
        spec = TOOLS.get(name)
        quota = spec.quota if spec else None
        try:
            if spec is None:
                raise ValueError("未知工具")
            args = spec.schema.model_validate(call["args"]).model_dump()
            request = context["request"]
            if not request.message_allowed:
                return {"id": ident, "success": False, "facts": ["今天聊天的次数已经用完了"], "sources": []}
            if quota and name not in request.tools:
                return {"id": ident, "success": False, "facts": ["今天这项次数已经用完了"], "sources": []}
            if quota:
                spent = context.setdefault("spent", {})
                if spent.get(quota, 0) >= request.tool_limits.get(quota, 1):
                    return {"id": ident, "success": False, "facts": ["这一项的可用次数已用完"], "sources": []}
                spent[quota] = spent.get(quota, 0) + 1
            if quota in {"toy", "image"}:
                if quota in context["used"]:
                    return {"id": ident, "success": False, "facts": ["本轮已经使用过这项能力"], "sources": []}
                context["used"].add(quota)
            await context["emit"]({"event": "status", "data": {"text": spec.progress}})
            async with asyncio.timeout(spec.timeout):
                result, cached = await self.invoke(name, args, context)
            context["toolUsage"].append(
                dict(
                    name=name,
                    kind=quota,
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
                    kind=quota,
                    success=False,
                    cached=False,
                    elapsedMs=round((time.monotonic() - start) * 1000),
                )
            )
            return {
                "id": ident,
                "success": False,
                "facts": ["工具执行失败，结果未知；没有提交后台任务"],
                "sources": [],
            }

    async def invoke(self, name, args, context):
        service, request = self.service, context["request"]
        if name in {"web_search", "read_link"}:
            query = args.get("query", request.content)
            result, cached = await web.search(
                service, request, query, **{k: v for k, v in args.items() if k != "query"}
            )
            context["sources"].extend(result["sources"])
            return result, cached
        if name == "search_images":
            found = await images.search(service, request, args["query"])
            context["images"].extend(found)
            return {"facts": [row["caption"] for row in found], "sources": []}, False
        if name == "draw_picture":
            if args.get("spontaneous") and not request.spontaneous_allowed:
                return {"facts": ["今天已经主动准备过一幅画了"]}, False
            context["drawings"].append(args)
            return {"facts": ["已登记后台画图，完成后另行通知，目前没有成品"]}, False
        if name == "make_toy":
            for attempt in range(2):
                try:
                    output = await service.plain(
                        "toy",
                        "只返回单文件HTML，内联脚本和样式，最多20KB，不引用外部资源。"
                        "简单功能优先采用最短的完整实现，不添加用户未要求的功能、装饰或说明。",
                        args["instruction"],
                    )
                    body = toy.secure(output)
                    review = await service.review_artifact(body)
                    if not review:
                        raise ValueError("小玩意未通过审核")
                    context["works"].append(dict(kind="TOY", title="一起做的小玩意", body=body))
                    return {"facts": ["小玩意已经制作完成，可以点击运行"]}, False
                except Exception:
                    if attempt:
                        raise
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
            web.WebFacts,
            "从资料中提炼可直接回答当前输入的短事实，每条给出资料下标。",
            {"input": request.content, "rows": rows},
        )
        if result is None or any(f.index >= len(rows) for f in result.facts):
            raise ValueError("工具事实缺少依据")
        return {"facts": [web.clean(f.content) for f in result.facts]}, False
