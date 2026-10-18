from pydantic import Field, field_validator

from app.schemas.models import Schema


class RetrievalRequest(Schema):
    query: str = Field(min_length=1, max_length=1000)
    candidates: list[str] = Field(max_length=20)
    limit: int = Field(default=5, ge=1, le=20)
    user_id: int = Field(default=0, ge=0)
    character: str = ""
    turn_id: str = ""
    time_zone: str = "Asia/Taipei"
    prior_tokens: int = Field(default=0, ge=0)

    @field_validator("candidates")
    @classmethod
    def bounded(cls, values: list[str]) -> list[str]:
        if any(len(value) > 1000 for value in values):
            raise ValueError("检索候选超过长度限制")
        return values


class RetrievalDecision(Schema):
    indexes: list[int] = Field(max_length=20)
    reason: str = Field(max_length=300)
