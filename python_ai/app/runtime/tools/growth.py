from app.runtime.tools.posts import extract


async def execute(service, args, context):
    user = context["request"].user_id
    rows = [await service.internal("meng-growth", f"/growth/internal/users/{user}/summary")]
    return await extract(service, rows, context)
