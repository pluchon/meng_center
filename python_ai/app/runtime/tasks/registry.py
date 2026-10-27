from dataclasses import dataclass, replace

from app.config.settings import TaskOptions
from app.prompts.companion import VERSION as COMPANION_VERSION
from app.prompts.templates import VERSION
from app.runtime.safety.judge import Judgment
from app.runtime.tools.facts import ToolFacts
from app.schemas.companion import Consolidation, ConsolidationReview, SessionSummary, SummaryReview
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
    version: str = VERSION
    temperature: float = 0
    timeout: float = 10
    retries: int = 1
    fallback: str = "unavailable"
    evaluation: str = "evals/moderation.jsonl"
    compact: bool = False


TASKS = {
    "understand": TaskSpec("judge", Understanding, evaluation="evals/topics.jsonl"),
    "moderate_text": TaskSpec("judge", Moderation),
    "moderate_image": TaskSpec("vision", Moderation, timeout=12, evaluation="tests/test_workflows.py"),
    "moderate_confirm": TaskSpec(
        "review", Moderation, timeout=12, fallback="suspect", evaluation="tests/test_workflows.py"
    ),
    "tags": TaskSpec("assist", Tags, evaluation="tests/test_workflows.py"),
    "inspirations": TaskSpec(
        "writer", Inspirations, temperature=0.7, timeout=15, evaluation="tests/test_boundary.py"
    ),
    "cover_plan": TaskSpec(
        "writer", CoverPlan, temperature=0.7, timeout=15, evaluation="tests/test_workflows.py"
    ),
    "growth": TaskSpec("assist", GrowthSummary, evaluation="tests/test_boundary.py"),
    "creator": TaskSpec("assist", Summary, evaluation="tests/test_boundary.py"),
    "memory_consolidate": TaskSpec(
        "memory",
        Consolidation,
        version=COMPANION_VERSION,
        temperature=0.2,
        timeout=15,
        evaluation="tests/test_companion_review.py",
    ),
    "memory_review": TaskSpec(
        "judge", ConsolidationReview, version=COMPANION_VERSION, evaluation="tests/test_companion_review.py"
    ),
    "summary": TaskSpec(
        "memory",
        SessionSummary,
        version=COMPANION_VERSION,
        temperature=0.2,
        timeout=15,
        evaluation="tests/test_companion_review.py",
    ),
    "summary_review": TaskSpec(
        "judge", SummaryReview, version=COMPANION_VERSION, evaluation="tests/test_companion_review.py"
    ),
    "reply_judge": TaskSpec(
        "judge", Judgment, version=COMPANION_VERSION, compact=True, evaluation="tests/test_companion.py"
    ),
    "tool_facts": TaskSpec(
        "judge", ToolFacts, version=COMPANION_VERSION, evaluation="tests/test_naitang_tools.py"
    ),
}


def configured(settings):
    overrides = getattr(settings, "task_overrides", {})
    if set(overrides) - TASKS.keys():
        raise ValueError("任务配置包含未知任务")
    result = {}
    for name, spec in TASKS.items():
        purpose_options = {
            key: value
            for key, value in getattr(settings, "model_overrides", {}).get(spec.purpose, {}).items()
            if key in {"temperature", "timeout"}
        }
        task_options = TaskOptions.model_validate(overrides.get(name, {})).model_dump(exclude_none=True)
        options = TaskOptions.model_validate({**purpose_options, **task_options}).model_dump(
            exclude_none=True
        )
        resolved = replace(spec, **options)
        if resolved.fallback == "suspect" and name != "moderate_confirm":
            raise ValueError("只有 BLOCK 复核允许降级为 SUSPECT")
        result[name] = resolved
    return result


def payload(value, exclude=()):
    def render(item):
        if isinstance(item, dict):
            return "\n".join(f"{key}：{render(content)}" for key, content in item.items())
        if isinstance(item, list):
            return "\n".join(render(content) for content in item)
        return str(item)

    return render(value.model_dump(by_alias=True, exclude=set(exclude)))
