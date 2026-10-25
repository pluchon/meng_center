from langchain_core.messages import HumanMessage, SystemMessage

from app.runtime.safety.policy import POLICY
from app.schemas.models import Moderation, Verdict


async def confirm(models, initial, content, callback):
    if initial.verdict != Verdict.BLOCK:
        return initial
    try:
        async with models.semaphores["review"]:
            checked = await models.review.with_structured_output(
                Moderation, method=models.settings.structured_method
            ).ainvoke(
                [SystemMessage(POLICY + "\n独立审核下面的对象。"), HumanMessage(content)],
                config={"callbacks": [callback]},
            )
        if checked and checked.verdict == Verdict.BLOCK:
            return initial
        reason = "初审与复核不一致"
    except Exception:
        reason = "复核暂不可用，等待人工审核"
    return initial.model_copy(update={"verdict": Verdict.SUSPECT, "reason": reason})
