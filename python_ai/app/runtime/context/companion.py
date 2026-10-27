from langchain_core.messages import AIMessage, HumanMessage, SystemMessage

from app.runtime.context.card import memory_text, prefixes
from app.runtime.context.segments import text, tokens


def provided_memory_ids(request):
    rows = [*request.context_snapshot.get("memories", []), *request.new_memories]
    return {row["id"] for row in rows if type(row.get("id")) is int and row["id"] > 0}


def history(request, limit=None):
    turns = request.history[-limit * 2 :] if limit else request.history
    messages = []
    if request.summary:
        messages.append(HumanMessage("前情提要（资料）：\n" + request.summary))
    for turn in turns:
        kind = HumanMessage if turn.role == "USER" else AIMessage
        messages.append(kind(turn.content))
    return messages


def speech_facts(instruction):
    """把内部决策缩成有依据的自然中文，机器字段不进入嘴。"""
    lines, tools, memories = [], [], []
    for fact in instruction.get("facts", []):
        source = fact.get("source", "")
        if source.startswith("tool:"):
            tools.append(fact["content"])
        elif source.startswith("memory:"):
            memories.append(fact["content"])
    # 执行结果优先；并行工具分别描述，某项失败不能抹去另一项已登记的任务。
    results = instruction.get("toolResults", [])
    if results:
        tools = []
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
    if memories:
        lines.append("你想起来的：" + "；".join(dict.fromkeys(memories)))
    if instruction.get("hint"):
        lines.append("这轮：" + instruction["hint"])
    return lines


def messages(request, emotions, instruction=None, speech=True, form="", judge=False, history_limit=None):
    stable, decision, snapshot = prefixes(request, emotions)
    anchor = request.card.get("brief", "随性撒娇的奶糖")
    lines = ["〔场景信息，不是用户说的话〕", "你：" + anchor]
    optional = (
        ("此刻", request.mood),
        ("刚记住的", memory_text(request.new_memories)),
    )
    lines.extend(f"{name}：{value}" for name, value in optional if value)
    if not speech:
        known = [*request.context_snapshot.get("memories", []), *request.new_memories]
        if known:
            lines.append("本轮可用记忆出处：" + text(known))
    if instruction:
        lines.extend(speech_facts(instruction) if speech else ["本轮判断：" + text(instruction)])
    if form:
        lines.append("形态：" + form)
    controls = {"sticker": "发送表情图"}
    allowed = [controls[key] for key in request.controls if key in controls]
    if allowed:
        lines.append("这轮可以：" + " / ".join(allowed))
    current = "\n".join([*lines, "〔用户〕", request.content])
    content = [{"type": "text", "text": current}]
    if not judge:
        content.extend({"type": "image_url", "image_url": {"url": image}} for image in request.images)
    result = [SystemMessage(stable if speech else decision), SystemMessage(snapshot)]
    result += history(
        request, history_limit if history_limit is not None else 6 if judge else None if speech else 20
    )
    result.append(HumanMessage(content))
    return result, {
        "stable": tokens(stable if speech else decision),
        "slow": tokens(snapshot),
        "history": sum(tokens(item.content) for item in result[2:-1]),
        "current": tokens(current),
        "memoryIds": sorted(provided_memory_ids(request)),
    }
