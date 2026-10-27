import re
from dataclasses import dataclass, field


@dataclass
class Parsed:
    bubbles: list[str] = field(default_factory=list)
    sticker: str | None = None


def parse(value, names, controls=(), character=""):
    result, lines = Parsed(), []
    raw = value.strip().splitlines()
    for index, line in enumerate(raw):
        line = line.strip()
        match = re.fullmatch(r"\[贴纸:([^\[\]\s]{1,12})\]", line)
        if match:
            name = match[1]
            if index == len(raw) - 1 and "sticker" in controls and name in names and result.sticker is None:
                result.sticker = name
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
    if not result.bubbles or any(len(chunk) > 500 for chunk in result.bubbles):
        raise ValueError("气泡为空或过长")
    return result
