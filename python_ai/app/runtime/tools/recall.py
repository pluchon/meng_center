from app.runtime.context.card import memory_text
from app.runtime.context.segments import text


async def execute(service, args, context):
    rows = context["request"].recalled
    ranked = await service.models.rerank.rank(args["query"], [text(row) for row in rows], scene="recall")
    selected = [rows[i] for i in ranked["indexes"]]
    return {
        "facts": [text(row) for row in selected],
        "speechFacts": [memory_text(selected)] if selected else [],
        "memoryIds": [row["id"] for row in selected],
    }
