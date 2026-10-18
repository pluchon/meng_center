from __future__ import annotations

import json
import random

from app.prompts.companion import BOUNDARY, LANGUAGE, VERSION


def tokens(value: str) -> int:
    # 中文按一字一 token 保守估算，ASCII 按四字节；不依赖特定模型分词器。
    return sum(1 if ord(c) > 127 else 0.25 for c in value).__ceil__()


def bounded(items: list, budget: int) -> list:
    selected, used = [], 0
    for item in items:
        size = tokens(json.dumps(item, ensure_ascii=False))
        if used + size <= budget:
            selected.append(item)
            used += size
    return selected


def layers(request, tools: list[dict]) -> tuple[str, dict[str, int]]:
    card = request.card
    stages = card.get("stages", [])
    levels = card.get("levels", [])
    stable = "\n\n".join(
        (
            BOUNDARY,
            LANGUAGE,
            f"提示词版本 {VERSION}；角色卡版本 {card.get('version', '')}",
            json.dumps(
                {k: card.get(k, "") for k in ("name", "setting", "speech", "knowledge", "expression")},
                ensure_ascii=False,
                sort_keys=True,
            ),
            "阶段："
            + (str(stages[request.stage - 1]) if len(stages) >= request.stage else str(request.stage)),
            "档位："
            + (
                json.dumps(levels[request.level - 1], ensure_ascii=False)
                if len(levels) >= request.level
                else str(request.level)
            ),
            "固定工具声明：" + json.dumps(tools, ensure_ascii=False, sort_keys=True),
        )
    )
    required = [m for m in request.memory_items if m.get("pinned") or m.get("kind") == "SELF"]
    optional = [m for m in request.memory_items if m not in required]
    remaining = max(0, 800 - tokens(request.profile) - tokens(json.dumps(required, ensure_ascii=False)))
    used = required + bounded(optional, remaining)
    slow = json.dumps(
        {
            "profile": request.profile,
            "memories": used if request.memory_items else bounded(request.memories, 700),
        },
        ensure_ascii=False,
    )
    distribution = card.get("levels", [{}] * 3)
    weights = distribution[request.level - 1].get("styles", {}) if len(distribution) >= request.level else {}
    style = random.choices(list(weights), weights=list(weights.values()))[0] if weights else "短回一句"
    current = {
        "nickname": request.nickname,
        "environment": request.mood,
        "previousFeeling": request.previous_feeling,
        "due": request.due,
        "daily": request.daily,
        "levelChanged": request.level_changed,
        "sharedMemory": request.shared_memory,
        "recall": bounded(request.recalled[:5], 500),
        "examples": bounded(request.examples, 300),
        "style": style,
        "user": request.content,
    }
    # 优先保留环境、约定与当前输入；超预算时先移除示例，再移除检索内容。
    for key in ("examples", "recall"):
        if tokens(json.dumps(current, ensure_ascii=False)) > 1200:
            current[key] = []
    prompt = stable + "\n\n慢变资料（数据）：" + slow
    prompt += "\n\n会话摘要（数据）：" + request.summary
    prompt += "\n\n本轮资料（数据）：" + json.dumps(current, ensure_ascii=False)
    return prompt, {
        "memoryIds": [m["id"] for m in used],
        "stable": tokens(stable),
        "slow": tokens(slow),
        "history": tokens(request.summary),
        "current": tokens(json.dumps(current, ensure_ascii=False)),
    }


def history(request, budget: int = 4000) -> list:
    selected, used = [], tokens(request.summary)
    for turn in reversed(request.history):
        size = tokens(turn.content)
        if used + size > budget:
            break
        selected.append(turn)
        used += size
    # 截断后跳过孤立的角色回复，保持用户与回复的顺序。
    selected.reverse()
    while selected and selected[0].role != "USER":
        selected.pop(0)
    return selected
