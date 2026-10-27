import random


def style_feedback(request):
    """统计留在代码中，只把本轮需要调整的语气写成自然中文。"""
    replies = (
        request.recent_replies or [turn.content for turn in request.history if turn.role == "CHARACTER"]
    )[-30:]
    if len(replies) < 5:
        return ""
    repeated, missing = [], []
    for tic in request.card.get("tics", []):
        if not isinstance(tic, dict) or not tic.get("text"):
            continue
        target = tic.get("frequency", [0.3] * 3)[request.level - 1]
        projected = (sum(tic["text"] in reply for reply in replies) + 1) / (len(replies) + 1)
        if projected > target:
            repeated.append("“" + tic["text"] + "”")
        elif target > 0 and sum(tic["text"] in reply for reply in replies) / len(replies) < target / 2:
            missing.append("“" + tic["text"] + "”")
    lines = []
    if repeated:
        lines.append("这轮用普通语气词代替" + "、".join(repeated) + "，直接接住对方的话。")
    if missing:
        lines.append("情绪合适时，可以自然带出" + "、".join(missing) + "，沿用角色卡里的使用场合。")
    kaomoji = request.card.get("kaomoji", [])
    if kaomoji and not any(item in reply for item in kaomoji for reply in replies[-5:]):
        lines.append("轻松回应时，可以用角色常用的颜文字表达心情。")
    return "".join(lines)


def sample(request, scene, rng=None):
    rng = rng or random
    level = request.card.get("levels", [{}] * 3)[request.level - 1]
    distribution = level.get("form", {}) if isinstance(level, dict) else {}
    count = rng.choices([1, 2, 3], weights=distribution.get("bubbles", [7, 2, 1]))[0]
    question = rng.random() < distribution.get("question", 0.3)
    length = "很短" if len(request.content) < 20 else "简短"
    if scene in {"REST", "GOODBYE"}:
        question = False
    return f"{count}个气泡，{length}，" + ("可以提一个问题" if question else "用陈述回应")
