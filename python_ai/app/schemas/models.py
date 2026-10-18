from __future__ import annotations

import re
from datetime import datetime
from enum import StrEnum
from typing import Annotated, Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator
from pydantic.alias_generators import to_camel


class Schema(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="ignore")


class Verdict(StrEnum):
    PASS = "PASS"
    SUSPECT = "SUSPECT"
    BLOCK = "BLOCK"


class Moderation(Schema):
    verdict: Verdict
    labels: list[str] = Field(default_factory=list, max_length=20)
    reason: str = Field(default="", max_length=200)


class Understanding(Moderation):
    topics: list[str] = Field(min_length=1, max_length=5)
    keywords: list[str] = Field(default_factory=list, max_length=8)
    quality: int = Field(ge=1, le=5)


class Draft(Schema):
    category: Literal["ANIME", "CREATION", "GAME", "TECH", "DAILY"]
    title: str = Field(default="", max_length=50)
    content: str = Field(default="", max_length=2000)


class TagRequest(Draft):
    candidates: list[str] = Field(default_factory=list, max_length=50)


class Tag(Schema):
    name: str = Field(min_length=1, max_length=12)
    existing: bool


class Tags(Schema):
    tags: list[Tag] = Field(min_length=3, max_length=3)


class Inspiration(Schema):
    angle: str = Field(min_length=1, max_length=100)
    explanation: str = Field(min_length=1, max_length=200)
    opening: str = Field(
        min_length=1,
        max_length=600,
        description="第二人称提问或客观说明形式的正文开头，不虚构作者经历、日期、数量或已完成成果。",
    )


class Inspirations(Schema):
    inspirations: list[Inspiration] = Field(min_length=3, max_length=3)


class GrowthStats(Schema):
    days: Literal[7, 30]
    daily_experience: list[dict[str, Any]] = Field(max_length=30)
    sources: list[dict[str, Any]] = Field(max_length=10)
    check_in_days: int = Field(ge=0, le=30)
    longest_streak: int = Field(ge=0, le=30)
    level: int = Field(ge=0)
    next_level_experience: int = Field(ge=0)


class CreatorStats(Schema):
    days: Literal[7, 30]
    daily_metrics: list[dict[str, Any]] = Field(max_length=30)
    post_count: int = Field(ge=0)
    best_posts: list[dict[str, Any]] = Field(max_length=3)
    categories: list[dict[str, Any]] = Field(max_length=5)
    posting_hours: list[dict[str, Any]] = Field(max_length=24)


_SENTENCE_END = re.compile(r"(?<=[。！？!?；;\n])")
_STATISTIC = re.compile(
    r"(?<![A-Za-z每])(?:\d+(?:\.\d+)?|[零〇一二三四五六七八九十百千万两]+)"
    r"\s*(?:天|级|%|％|点经验|次|篇|条|收藏|浏览|喜欢|评论)"
)
_LEVEL = re.compile(r"等级|升级|晋级|解锁")


def _without(value: str, pattern: re.Pattern[str], message: str) -> str:
    # 模型偶尔会在某一句里写出统计数字或等级：整句去掉，剩下的照用；一句不剩才算不合格并重试
    kept = "".join(s for s in _SENTENCE_END.split(value) if s.strip() and not pattern.search(s)).strip()
    if not kept:
        raise ValueError(message)
    return kept


class Summary(Schema):
    summary: str = Field(
        min_length=1,
        max_length=600,
        description="只总结统计支持的活动，不复述数字或虚构升级、成果、情绪；建议放 suggestions。",
    )
    suggestions: list[str] = Field(
        min_length=1,
        max_length=3,
        description="具体可实行的内容或互动建议；不虚构读者活跃时段，不推荐无统计依据的发布时间。",
    )

    @field_validator("summary")
    @classmethod
    def numbers_belong_to_java(cls, value: str) -> str:
        # Topic names such as Vue3 remain valid; generated statistical claims do not.
        return _without(value, _STATISTIC, "Keep statistical numbers in the Java metrics, not the narrative")


class GrowthSummary(Summary):
    @field_validator("summary")
    @classmethod
    def level_history_is_not_available(cls, value: str) -> str:
        return _without(value, _LEVEL, "Leave levels to the Java metrics; no level history was supplied")


class CoverRequest(Schema):
    title: str = Field(default="", max_length=50)
    summary: str = Field(default="", max_length=2000)
    category: str = Field(max_length=20)


class CoverPlan(Schema):
    prompt: str = Field(min_length=1, max_length=1000)
    negative_prompt: str = Field(default="", max_length=500)


class EmbeddingsRequest(Schema):
    texts: list[Annotated[str, Field(min_length=1, max_length=6000)]] = Field(min_length=1, max_length=10)


class TrainingSample(Schema):
    features: list[float] = Field(min_length=1, max_length=32)
    clicked: bool
    occurred_at: datetime


class TrainingRequest(Schema):
    samples: list[TrainingSample] = Field(min_length=2, max_length=100_000)


class Event(Schema):
    event_id: str = Field(min_length=1, max_length=64)
    event_type: str
    occurred_at: datetime
    trace_id: str
    body: dict[str, Any]


class Image(Schema):
    id: int
    data: str = Field(max_length=500_000)

    @field_validator("data")
    @classmethod
    def jpeg_only(cls, value: str) -> str:
        import base64

        raw = base64.b64decode(value, validate=True)
        if not raw.startswith(b"\xff\xd8\xff"):
            raise ValueError("Expected JPEG thumbnail")
        return value


class PostInput(Schema):
    post_id: int
    author_id: int
    version: str
    category: str
    title: str = Field(max_length=50)
    plain_text: str = Field(max_length=20000)
    tags: list[str] = Field(default_factory=list, max_length=50)
    image_thumbs: list[Image] = Field(default_factory=list, max_length=11)
    images_changed: bool = True


class ModerationInput(Schema):
    comment_id: int | None = None
    user_id: int | None = None
    version: str = ""
    content: str = Field(default="", max_length=500)
    nickname: str = Field(default="", max_length=50)
    signature: str = Field(default="", max_length=200)
    image_thumbs: list[Image] = Field(default_factory=list, max_length=10)
    avatar_thumb: Image | None = None
    avatar_changed: bool = False
