import re
from dataclasses import dataclass, field


@dataclass
class Parsed:
    bubbles: list[str] = field(default_factory=list)
    reaction: str | None = None
    retract_index: int | None = None
    sticker: str | None = None


def parse(value, names, controls=(), character=""):
    result, lines = Parsed(), []
    for line in value.strip().splitlines():
        line = line.strip()
        match = re.fullmatch(r"\[(回应|贴纸):([^\[\]\s]{1,12})\]", line)
        if match:
            kind, name = match.groups()
            key = "reaction" if kind == "回应" else "sticker"
            if key in controls and name in names and getattr(result, key) is None:
                setattr(result, key, name)
            continue
        if line == "[撤回]":
            if "retract" in controls and result.retract_index is None:
                before = "\n".join(lines).strip()
                if before:
                    result.retract_index = min(2, len(re.split(r"\n\s*\n", before)) - 1)
            continue
        # 不符合控制协议的行不进入可见文本。
        if re.match(r"^\[(?:回应|贴纸|撤回)", line):
            continue
        line = re.sub(r"^(?:#{1,6}\s+|[-*+]\s+)", "", line)
        line = re.sub(r"\*\*|__|`", "", line)
        if character:
            line = re.sub(r"^" + re.escape(character) + r"[：:]\s*", "", line)
        lines.append(line)
    chunks = [chunk.strip() for chunk in re.split(r"\n\s*\n", "\n".join(lines)) if chunk.strip()]
    result.bubbles = chunks[:2] + (["\n".join(chunks[2:])] if len(chunks) > 2 else [])
    if (not result.bubbles and not result.reaction) or any(len(chunk) > 500 for chunk in result.bubbles):
        raise ValueError("气泡为空或过长")
    return result
