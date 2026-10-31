import json

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage

from app.runtime.context.card import memory_text, prefixes
from app.runtime.context.segments import text, tokens


def provided_memory_ids(request):
    rows = [*request.context_snapshot.get("memories", []), *request.new_memories]
    return {row["id"] for row in rows if type(row.get("id")) is int and row["id"] > 0}


def guarded(value, *, single_line=False):
    """用户文字进提示词前去掉结构标记，防止伪造场景信息或历史发言。"""
    value = value.replace("〔", "「").replace("〕", "」")
    return " ".join(value.splitlines()) if single_line else value


def history(request, limit=None, *, reference=False, compact=False):
    turns = request.history[-limit * 2 :] if limit else request.history
    messages = []
    if request.summary:
        messages.append(HumanMessage("前情提要（资料）：\n" + request.summary))
    for turn in turns:
        kind = HumanMessage if turn.role == "USER" else AIMessage
        content = turn.content
        if reference:
            if compact:
                label = "历史用户" if turn.role == "USER" else "历史奶糖"
                content = f"{label}：{guarded(content, single_line=True)}"
            else:
                label = "历史用户发言" if turn.role == "USER" else "奶糖历史发言"
                content = f"〔{label}〕\n{guarded(content)}"
        messages.append(kind(content))
    return messages


def speech_facts(instruction):
    """把内部决策缩成有依据的自然中文，机器字段不进入嘴。"""
    lines, tools, memories = [], [], []
    # 并行工具分别描述，某项失败不能抹去另一项已登记的任务。
    results = instruction.get("toolResults", [])
    for result in results:
        if not result.get("success", True):
            tools.append(
                "这次操作没有成功；" + "；".join(result.get("facts", [])) + "。这项操作没有后台任务。"
            )
        elif result.get("name") == "recall":
            memories.extend(result.get("speechFacts", []))
        else:
            tools.extend(result.get("facts", []) or ["这次没有找到可用的结果"])
    if tools:
        lines.append("你刚查到的：" + "；".join(dict.fromkeys(tools)))
    if any(row.get("type") == "WEB" for result in results if result.get("success", True)
           for row in result.get("sources", [])):
        # 来源链接由页面显示在气泡下方，嘴不知道就会说「拿不出来源」，和页面矛盾。
        lines.append("查到的网页链接会自动显示在你的回复下面，不用念网址，也不要说没有来源。")
    if memories:
        lines.append("你想起来的：" + "；".join(dict.fromkeys(memories)))
    if instruction.get("image_note"):
        # 嘴偶尔读不出图，决策器看到的内容给它兜底，不至于回一句「看不到」。
        lines.append("图里是：" + instruction["image_note"])
    if instruction.get("hint"):
        lines.append("这轮：" + instruction["hint"])
    return lines


def messages(request, emotions, instruction=None, speech=True, form="", judge=False, history_limit=None):
    stable, decision, snapshot = prefixes(request, emotions)
    anchor = request.card.get("brief", "随性撒娇的奶糖")
    is_agent = not speech and not judge
    # 决策器的角色资料已在稳定前缀中；当前消息只保留本轮数据。
    lines = [] if is_agent else [
        "〔场景信息，不是用户说的话〕", ("你：" if speech else "参考角色：") + anchor
    ]
    optional = (
        ("对话时钟" if is_agent else "对话时钟（只用于当前问候与日期计算）", request.mood),
        ("刚记住的", memory_text(request.new_memories)),
    )
    lines.extend(f"{name}：{value}" for name, value in optional if value)
    if not speech:
        if not request.recalled:
            lines.append("额外记忆候选：无" if is_agent else
                         "记忆检索：没有额外记忆候选，recall不可用；已有前文可直接阅读，指代不明时询问。")
        known = [*request.context_snapshot.get("memories", []), *request.new_memories]
        if known:
            lines.append("本轮可用记忆出处：" + text(known))
    if instruction and not judge:
        lines.extend(speech_facts(instruction) if speech else ["本轮判断：" + text(instruction)])
    if form:
        lines.append("形态：" + form)
    controls = {"sticker": "发送表情图"}
    allowed = [controls[key] for key in request.controls if key in controls]
    if allowed:
        lines.append("这轮可以：" + " / ".join(allowed))
    current = json.dumps(
        {"场景资料": lines, "用户原话": request.content, **(instruction or {})},
        ensure_ascii=False, separators=(",", ":"),
    ) if judge else "\n".join([*lines, "用户：" if is_agent else "〔用户〕", guarded(request.content)])
    content = [{"type": "text", "text": current}]
    if not judge:
        content.extend({"type": "image_url", "image_url": {"url": image}} for image in request.images)
    result = [SystemMessage(stable if speech else decision), SystemMessage(snapshot)]
    result += history(
        request, history_limit if history_limit is not None else 6 if judge else None if speech else 20,
        reference=not speech, compact=is_agent,
    )
    result.append(HumanMessage(content))
    return result, {
        "stable": tokens(stable if speech else decision),
        "slow": tokens(snapshot),
        "history": sum(tokens(item.content) for item in result[2:-1]),
        "current": tokens(current),
        "memoryIds": sorted(provided_memory_ids(request)),
    }
