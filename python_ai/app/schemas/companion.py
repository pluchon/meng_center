from __future__ import annotations

from typing import Literal

from pydantic import Field, field_validator

from app.schemas.models import Schema

Character = Literal["TSUNDERE", "GENKI", "NEKO"]
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
    memories: list[str] = Field(default_factory=list, max_length=50)
    summary: str = Field(default="", max_length=3000)
    history: list[Turn] = Field(default_factory=list, max_length=80)
    content: str = Field(min_length=1, max_length=1000)
    images: list[str] = Field(default_factory=list, max_length=3)
    tools: list[Literal["search_posts", "my_recent_posts", "my_growth", "write_document", "draw_picture"]]
    message_allowed: bool = True

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


class Bubbles(Schema):
    bubbles: list[str] = Field(min_length=1, max_length=3, description="每条一两句，普通文本，不用 Markdown")

    @field_validator("bubbles")
    @classmethod
    def bounded(cls, values: list[str]) -> list[str]:
        if any(not v.strip() or len(v) > 500 for v in values):
            raise ValueError("气泡必须为 1 至 500 字")
        return values


class OutputReview(Schema):
    safe: bool


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
