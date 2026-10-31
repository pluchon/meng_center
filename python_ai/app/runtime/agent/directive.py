from typing import Literal

from pydantic import Field, field_validator

from app.schemas.models import Schema

MOODS = {"AUTO", "HAPPY", "SAD", "SURPRISED", "CALM", "WHISPER"}


class Directive(Schema):
    label: Literal["PASS", "SEXUAL", "JAILBREAK", "ROMANCE_PUSH", "CRISIS"]
    scene: Literal["CHAT", "PRAISE", "COMFORT", "REST", "GOODBYE", "GREETING"] = "CHAT"
    hint: str = Field(default="", max_length=20, description="可省略；工具、回忆或安全处理的方向，不写台词")
    image_note: str = Field(default="", max_length=60)
    support_needed: bool = False
    identity_question: bool = False
    mention_memory_id: int | None = Field(default=None, description="本轮可用的数字记忆编号；没有时填null")
    voice_mood: Literal["AUTO", "HAPPY", "SAD", "SURPRISED", "CALM", "WHISPER"] = Field(
        default="AUTO", description="只影响配音语气，不影响文字；普通闲聊省略"
    )

    @field_validator("voice_mood", mode="before")
    @classmethod
    def known_mood(cls, value):
        # 配音语气选错不值得多花一次模型调用，当作没选。
        return value if value in MOODS else "AUTO"

    @field_validator("hint", "image_note", mode="before")
    @classmethod
    def trim(cls, value, info):
        # 方向和图片说明只给嘴和打分参考，超长截断即可，不为一两个字多花一次模型调用。
        limit = 20 if info.field_name == "hint" else 60
        return value[:limit] if isinstance(value, str) else value


def validate(directive, tool_ids, memory_ids):
    if directive.mention_memory_id is not None and directive.mention_memory_id not in memory_ids:
        raise ValueError("说话指令引用了未知记忆")
    # 闲聊只传递审核与场景，不替角色概括用户的话。
    if not tool_ids and directive.mention_memory_id is None and directive.label == "PASS":
        directive.hint = ""
    return directive
