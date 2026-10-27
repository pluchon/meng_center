from __future__ import annotations

import asyncio
from typing import Any

from langchain_core.exceptions import OutputParserException
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.runnables import Runnable, RunnableConfig, RunnableLambda
from pydantic import BaseModel, ValidationError

from app.prompts.templates import SAFETY, TASKS
from app.runtime.safety.policy import POLICY


def limited(chain: Runnable, semaphore: asyncio.Semaphore) -> Runnable:
    async def invoke(value: dict[str, Any], config: RunnableConfig) -> Any:
        async with semaphore:
            return await chain.ainvoke(value, config=config)

    return RunnableLambda(invoke)


def structured(model: Runnable, schema: type[BaseModel], method: str, attempts=2) -> Runnable:
    return model.with_structured_output(schema, method=method).with_retry(
        stop_after_attempt=attempts,
        retry_if_exception_type=(OutputParserException, ValidationError),
    )


def build_chain(model: Runnable, schema: type[BaseModel], task: str, method: str) -> Runnable:
    prompt = ChatPromptTemplate.from_messages(
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
    return prompt | structured(model, schema, method)
