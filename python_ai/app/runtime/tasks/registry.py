from dataclasses import dataclass

from app.schemas.models import (
    CoverPlan,
    GrowthSummary,
    Inspirations,
    Moderation,
    Summary,
    Tags,
    Understanding,
)


@dataclass(frozen=True)
class TaskSpec:
    purpose: str
    schema: type
    version: str = "20261024.1"
    temperature: float = 0
    timeout: float = 10
    retries: int = 1
    fallback: str = "unavailable"
    evaluation: str = "evals/moderation.jsonl"


TASKS = {
    "understand": TaskSpec("judge", Understanding, evaluation="evals/topics.jsonl"),
    "moderate_text": TaskSpec("judge", Moderation),
    "moderate_image": TaskSpec("vision", Moderation),
    "moderate_confirm": TaskSpec("review", Moderation, fallback="suspect"),
    "tags": TaskSpec("assist", Tags),
    "inspirations": TaskSpec("writer", Inspirations, temperature=0.7, timeout=15),
    "cover_plan": TaskSpec("writer", CoverPlan, temperature=0.7, timeout=15),
    "growth": TaskSpec("assist", GrowthSummary),
    "creator": TaskSpec("assist", Summary),
    "retrieval": TaskSpec("rerank", dict, fallback="vector"),
}


def payload(value, exclude=()):
    def render(item):
        if isinstance(item, dict):
            return "\n".join(f"{key}：{render(content)}" for key, content in item.items())
        if isinstance(item, list):
            return "\n".join(render(content) for content in item)
        return str(item)

    return render(value.model_dump(by_alias=True, exclude=set(exclude)))
