import html
import re
import unicodedata

from pydantic import Field

from app.schemas.models import Schema


class ToolFact(Schema):
    content: str = Field(min_length=1, max_length=600)
    index: int = Field(ge=0)


class ToolFacts(Schema):
    facts: list[ToolFact] = Field(max_length=5)


def clean(value):
    value = html.unescape(re.sub(r"<[^>]*>", "", value))
    value = re.sub(r"\[([^]]+)\]\([^)]*\)|https?://\S+", lambda m: m[1] or "", value)
    return "".join(
        c for c in unicodedata.normalize("NFKC", value)
        if not unicodedata.category(c).startswith("C") or c == "\n"
    )[:600]
