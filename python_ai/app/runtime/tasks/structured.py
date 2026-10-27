from __future__ import annotations

from langchain_core.exceptions import OutputParserException
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.runnables import Runnable, RunnableLambda
from pydantic import BaseModel, ValidationError

from app.prompts.templates import SAFETY, TASKS
from app.runtime.safety.policy import POLICY
from app.runtime.tasks.registry import configured
from app.schemas.models import Moderation, Verdict

REVIEW_UNAVAILABLE = "复核暂不可用，等待人工审核"


def structured(
    model: Runnable, schema, method: str, attempts=2, *, options=None, validation_schema=None
) -> Runnable:
    def validate(value):
        target = validation_schema or schema
        if isinstance(target, type) and issubclass(target, BaseModel):
            return target.model_validate(value)
        if value is None:
            raise OutputParserException("缺少结构化输出")
        return value

    chain = model.with_structured_output(schema, method=method).bind(**(options or {})) | RunnableLambda(
        validate
    )
    return chain.with_retry(
        stop_after_attempt=attempts,
        retry_if_exception_type=(OutputParserException, ValidationError),
    )


def task_prompt(task):
    return ChatPromptTemplate.from_messages(
        [
            (
                "system",
                (POLICY if task in {"understand", "moderate_text", "cover_plan"} else SAFETY)
                + "\n"
                + TASKS[task],
            ),
            ("human", "<post>\n{payload}\n</post>"),
        ]
    )


class TaskRuntime:
    def __init__(self, models):
        self.models = models
        self.specs = configured(models.settings)
        self.outputs = {}

    def output(self, name, *, attempts=None):
        key = (name, attempts)
        if key in self.outputs:
            return self.outputs[key]
        spec = self.specs[name]
        schema = spec.schema
        if spec.compact:
            schema = {
                "name": spec.schema.__name__,
                "description": spec.schema.__doc__,
                "parameters": spec.schema.compact_tool_schema(),
            }
        chain = structured(
            getattr(self.models, spec.purpose),
            schema,
            self.models.settings.structured_method,
            attempts=spec.retries + 1 if attempts is None else attempts,
            validation_schema=spec.schema,
            options={
                "temperature": spec.temperature,
                "timeout": spec.timeout,
                "_runtime_retries": spec.retries,
            },
        )
        if spec.fallback == "suspect":
            chain = chain.with_fallbacks(
                [RunnableLambda(lambda _: Moderation(verdict=Verdict.SUSPECT, reason=REVIEW_UNAVAILABLE))]
            )
        chain = chain.with_config(
            metadata={"task": name, "taskVersion": spec.version, "evaluationSet": spec.evaluation}
        )
        self.outputs[key] = chain
        return chain

    def chain(self, name):
        return task_prompt(name) | self.output(name)

    def name_for_schema(self, schema):
        names = [name for name, spec in self.specs.items() if spec.schema is schema]
        if len(names) != 1:
            raise ValueError("结构化任务必须唯一登记")
        return names[0]
