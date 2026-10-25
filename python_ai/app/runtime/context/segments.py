from dataclasses import dataclass
from enum import StrEnum


class Stability(StrEnum):
    STABLE = "STABLE"
    EPOCH = "EPOCH"
    APPEND = "APPEND"
    TURN = "TURN"


def tokens(value):
    return sum(1 if ord(c) > 127 else 0.25 for c in str(value)).__ceil__()


def text(value):
    if isinstance(value, dict):
        return "\n".join(f"{key}：{text(value[key])}" for key in sorted(value))
    if isinstance(value, list):
        return "\n".join(text(item) for item in value)
    return str(value) if value is not None else ""


@dataclass(frozen=True)
class Segment:
    name: str
    stability: Stability
    content: str


def should_compact(idle_seconds, history_tokens, context_limit=600_000):
    return history_tokens >= min(600_000, context_limit) or (idle_seconds >= 1800 and history_tokens > 8000)
