import html
import re
import unicodedata

from pydantic import Field, PositiveInt

from app.schemas.models import Schema


class ToolFact(Schema):
    content: str = Field(min_length=1, max_length=600)
    index: int = Field(ge=0)


class ToolFacts(Schema):
    facts: list[ToolFact] = Field(max_length=5)


class Source(Schema):
    post_id: PositiveInt
    title: str = Field(min_length=1, max_length=200)


class ToolResult(Schema):
    facts: list[str] = Field(default_factory=list, max_length=5)
    speech_facts: list[str] = Field(default_factory=list, max_length=5)
    sources: list[Source] = Field(default_factory=list, max_length=5)
    memory_ids: list[PositiveInt] = Field(default_factory=list, max_length=20)


def clean_result(value):
    result = ToolResult.model_validate(value)
    result.facts = [clean(item) for item in result.facts]
    result.speech_facts = [clean(item, limit=3000) for item in result.speech_facts]
    result.sources = [
        source.model_copy(update={"title": clean(source.title)[:200]}) for source in result.sources
    ]
    if any(not source.title.strip() for source in result.sources):
        raise ValueError("帖子来源标题为空")
    return result.model_dump(by_alias=True)


def clean(value, limit=600):
    value = html.unescape(re.sub(r"<[^>]*>", "", value))
    value = re.sub(r"\[([^]]+)\]\([^)]*\)|https?://\S+", lambda m: m[1] or "", value)
    return "".join(
        c
        for c in unicodedata.normalize("NFKC", value)
        if not unicodedata.category(c).startswith("C") or c == "\n"
    )[:limit]
