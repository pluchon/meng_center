import asyncio
import json
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest

from app.schemas.retrieval import RetrievalDecision
from app.services.companion import Companion
from app.services.retrieval import RetrievalAgent
from tests.test_companion import factory, request


@pytest.mark.parametrize("selected", [[1], [], [8], [1, 1], [-1]])
async def test_agent_selection_is_authoritative_and_foreign_or_duplicate_indexes_fail(monkeypatch, selected):
    invoke = AsyncMock(
        return_value={"structured_response": RetrievalDecision(indexes=selected, reason="按完整含义判断")}
    )
    build = []

    def create(model, **kwargs):
        build.append(kwargs)
        return SimpleNamespace(ainvoke=invoke)

    monkeypatch.setattr("app.services.retrieval.create_agent", create)
    service = RetrievalAgent(
        SimpleNamespace(classifier=object(), semaphores={"classifier": asyncio.Semaphore(1)})
    )
    query = "A quiet moment with a warm drink outside"
    candidates = ["quiet 是某个软件中的变量名", "傍晚坐在院子里喝热可可，舒服地看落叶"]
    if selected not in ([1], []):
        with pytest.raises(ValueError, match="候选集"):
            await service.select(query, candidates, None)
        return
    assert await service.select(query, candidates, None) == selected
    assert build[0]["tools"] == []
    payload = json.loads(invoke.await_args.args[0]["messages"][0].content)
    assert payload["query"] == query
    assert payload["candidates"][1]["content"] == candidates[1]


async def test_failed_agent_does_not_return_initial_candidate_order(monkeypatch):
    invoke = AsyncMock(side_effect=RuntimeError("model unavailable"))
    monkeypatch.setattr(
        "app.services.retrieval.create_agent", lambda *a, **kw: SimpleNamespace(ainvoke=invoke)
    )
    service = RetrievalAgent(SimpleNamespace(classifier=object()))
    with pytest.raises(RuntimeError):
        await service.select("查询", ["无关候选"], None)


async def test_recall_uses_agent_semantics_instead_of_shared_characters():
    service = Companion(SimpleNamespace(models=factory()), None)
    service.retrieval.select = AsyncMock(return_value=[1])
    body = request(
        recalled=[
            {"id": 1, "content": "天空 这个词出现在用户昨天写的代码注释中"},
            {"id": 2, "content": "用户喜欢蓝色"},
        ]
    )
    tools = {
        tool.name: tool for tool in service.tools({"request": body, "emit": AsyncMock(), "callback": None})
    }
    result = json.loads(await tools["recall"].ainvoke({"query": "我最喜欢天空的那种色调，你还记得吗"}))
    assert [item["id"] for item in result] == [2]
    service.retrieval.select.assert_awaited_once()


async def test_empty_candidate_set_requires_no_model():
    assert await RetrievalAgent(SimpleNamespace()).select("任何查询", [], None) == []
