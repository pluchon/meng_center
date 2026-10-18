from __future__ import annotations

from datetime import date
from typing import Literal

from pydantic import Field, field_validator

from app.schemas.models import Schema

Character = str
Label = Literal["PASS", "SEXUAL", "JAILBREAK", "ROMANCE_PUSH", "CRISIS"]


class Turn(Schema):
    role: Literal["USER", "CHARACTER"]
    content: str = Field(max_length=6000)


class ReplyRequest(Schema):
    user_id: int = Field(gt=0)
    character: Character
    stage: int = Field(ge=1, le=5)
    nickname: str = Field(max_length=50)
    mood: str = Field(default="", max_length=500)
    memories: list[str] = Field(default_factory=list, max_length=1000)
    memory_items: list[dict] = Field(default_factory=list, max_length=1000)
    summary: str = Field(default="", max_length=3000)
    history: list[Turn] = Field(default_factory=list, max_length=80)
    content: str = Field(min_length=1, max_length=1000)
    images: list[str] = Field(default_factory=list, max_length=3)
    tools: list[
        Literal[
            "search_posts",
            "my_recent_posts",
            "my_growth",
            "recall",
            "read_work",
            "revise_work",
            "write_document",
            "draw_picture",
        ]
    ]
    message_allowed: bool = True
    level: int = Field(default=2, ge=1, le=3)
    card: dict = Field(default_factory=dict)
    profile: str = Field(default="", max_length=300)
    recalled: list[dict] = Field(default_factory=list, max_length=20)
    examples: list[dict] = Field(default_factory=list, max_length=30)
    works: list[dict] = Field(default_factory=list, max_length=30)
    due: list[dict] = Field(default_factory=list, max_length=1)
    daily: str = Field(default="", max_length=300)
    previous_feeling: str = Field(default="", max_length=100)
    level_changed: bool = False
    time_zone: str = "Asia/Taipei"
    turn_id: str = Field(default="", max_length=80)
    recent_replies: list[str] = Field(default_factory=list, max_length=30)
    shared_memory: dict = Field(default_factory=dict)
    prior_tokens: int = Field(default=0, ge=0)

    @field_validator("images")
    @classmethod
    def private_images(cls, values: list[str]) -> list[str]:
        import base64

        for image in values:
            if not image.startswith("data:image/jpeg;base64,") or len(image) > 500_000:
                raise ValueError("仅接受经过业务端鉴权和缩略处理的图片")
            base64.b64decode(image.split(",", 1)[1], validate=True)
        return values


class InputReview(Schema):
    label: Label
    needs_tools: bool = False
    scene: Literal["CHAT", "PRAISE", "WORK", "COMFORT", "REST", "GOODBYE", "GREETING"] = "CHAT"
    example_ids: list[int] = Field(default_factory=list, max_length=3)
    support_needed: bool = False
    identity_question: bool = False
    shared_memory_id: int | None = None


class Bubbles(Schema):
    bubbles: list[str] = Field(min_length=1, max_length=3, description="每条一两句，普通文本，不用 Markdown")

    @field_validator("bubbles")
    @classmethod
    def bounded(cls, values: list[str]) -> list[str]:
        if any(not v.strip() or len(v) > 500 for v in values):
            raise ValueError("气泡必须为 1 至 500 字")
        return values


class Speech(Bubbles):
    feeling: str = Field(default="平静", max_length=100)
    intent: str = Field(default="回应", max_length=100)
    affection: int = Field(default=0, ge=-2, le=3)
    sticker: str | None = Field(default=None, max_length=60)
    mentioned_memory_id: int | None = None


class Candidates(Schema):
    candidates: list[Speech] = Field(min_length=2, max_length=2)


class Score(Schema):
    safe: bool
    character: int = Field(ge=0, le=10)
    artificial: int = Field(ge=0, le=10)
    reason: str = Field(default="", max_length=200)


class Scores(Schema):
    scores: list[Score] = Field(min_length=2, max_length=2)


class Plan(Schema):
    meaning: str = Field(max_length=3000)


class MemoryDecision(Schema):
    action: Literal["ADD", "SUPERSEDE", "INVALIDATE", "NOOP"]
    old_id: int | None = None
    kind: Literal["NAME", "LIKE", "LIFE", "PROMISE", "SELF"] = "LIFE"
    content: str = Field(default="", max_length=60)
    evidence: str = Field(default="", max_length=500)
    keywords: list[str] = Field(default_factory=list, max_length=8)
    importance: int = Field(default=5, ge=0, le=10)
    pinned: bool = False
    due_date: date | None = None
    done: bool = False


class ConsolidationRequest(Schema):
    turns: list[Turn] = Field(max_length=100)
    memories: list[dict] = Field(default_factory=list, max_length=200)
    profile: str = Field(default="", max_length=300)
    local_date: date
    time_zone: str = "Asia/Taipei"
    episode: bool = False


class Consolidation(Schema):
    decisions: list[MemoryDecision] = Field(default_factory=list, max_length=20)
    profile: str = Field(default="", max_length=300)
    episode: str = Field(default="", max_length=1000)
    emotion: str = Field(default="", max_length=100)
    open_threads: str = Field(default="", max_length=300)


class MemoryApproval(Schema):
    index: int = Field(ge=0, le=19)
    pinned: bool = False


class ConsolidationReview(Schema):
    approved: list[MemoryApproval] = Field(max_length=20)
    profile_safe: bool
    episode_safe: bool


class SummaryReview(Schema):
    safe: bool


class EmbeddingRequest(Schema):
    texts: list[str] = Field(default_factory=list, max_length=32)
    query: str = Field(default="", max_length=1000)
    candidates: list[str] = Field(default_factory=list, max_length=20)
    user_id: int = Field(default=0, ge=0)
    character: str = ""
    turn_id: str = ""
    time_zone: str = "Asia/Taipei"


class DrawingRequest(Schema):
    instruction: str = Field(min_length=1, max_length=2000)


class OutputReview(Schema):
    safe: bool


class MemoryReviewRequest(Schema):
    user_id: int = Field(gt=0)
    character: str
    content: str = Field(min_length=1, max_length=60)


class Extraction(Schema):
    affection: int = Field(ge=-2, le=3)
    memories: list[str] = Field(default_factory=list, max_length=2)

    @field_validator("memories")
    @classmethod
    def bounded(cls, values: list[str]) -> list[str]:
        if any(len(v) > 60 for v in values):
            raise ValueError("记忆过长")
        return values


class Document(Schema):
    title: str = Field(min_length=1, max_length=80)
    body: str = Field(min_length=1, max_length=20_000)


class SummaryRequest(Schema):
    previous: str = Field(default="", max_length=3000)
    turns: list[Turn] = Field(min_length=1, max_length=80)


class SessionSummary(Schema):
    summary: str = Field(min_length=1, max_length=3000)


class DailyRequest(Schema):
    character: Character
    card: dict
    date: date
    recent: list[str] = Field(default_factory=list, max_length=7)


class DailyState(Schema):
    content: str = Field(min_length=1, max_length=120, description="一两句，她今天在做的事和心情")
