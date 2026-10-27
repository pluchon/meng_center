from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from test_companion import request

from app.main import app
from app.runtime.tools.executor import Executor
from app.runtime.tools.facts import clean_result
from app.runtime.tools.registry import TOOLS


def test_read_only_tools_and_internal_contract_are_closed():
    assert set(TOOLS) == {"recall", "search_posts", "read_post", "my_recent_posts", "my_growth"}
    paths = {
        route.path.rsplit("/", 1)[-1]
        for route in app.routes
        if getattr(route, "path", "").startswith("/ai/internal/companion/")
    }
    assert paths == {"replies", "summaries", "consolidations", "embeddings"}


def executor(monkeypatch, **overrides):
    stored = {}

    async def save(key, value, **kwargs):
        stored[key] = value

    redis = SimpleNamespace(get=AsyncMock(side_effect=stored.get), set=AsyncMock(side_effect=save))
    service = SimpleNamespace(
        models=SimpleNamespace(redis=redis, settings=SimpleNamespace(**overrides)),
        prompt_version=lambda req: "fixture-version",
    )
    monkeypatch.setattr("app.runtime.tools.executor.get_stream_writer", lambda: lambda event: None)
    value = Executor(service)
    value.invoke = AsyncMock(
        return_value={"facts": ["Ａ\u200b<b>事实</b>"], "sources": [{"postId": 12, "title": "<b>标题</b>"}]}
    )
    return value, stored, redis


def context(**overrides):
    return dict(request=request(**overrides), memoryIds=set(), sources=[], toolUsage=[])


async def test_cache_hit_restores_clean_facts_and_sources_without_executing_again(monkeypatch):
    value, _, _ = executor(monkeypatch)
    call = {"id": "a", "name": "read_post", "args": {"post_id": 12}}
    await value.execute(call, context())
    current = context()
    result = await value.execute({**call, "id": "b"}, current)
    assert result["success"] and result["id"] == "b" and result["facts"] == ["A事实"]
    assert current["sources"] == [{"postId": 12, "title": "标题"}]
    assert current["toolUsage"][0]["cached"]
    value.invoke.assert_awaited_once()


async def test_user_and_recall_candidates_isolate_cached_memory(monkeypatch):
    value, _, _ = executor(monkeypatch)

    async def recalled(name, args, current):
        ident = current["request"].recalled[0]["id"]
        return {"facts": ["喜欢蓝色"], "memoryIds": [ident]}

    value.invoke.side_effect = recalled
    call = {"id": "a", "name": "recall", "args": {"query": "喜欢什么"}}
    first = context(recalled=[{"id": 10, "content": "喜欢蓝色"}])
    await value.execute(call, first)
    hit = context(recalled=[{"id": 10, "content": "喜欢蓝色"}])
    await value.execute(call, hit)
    assert hit["memoryIds"] == {10} and hit["toolUsage"][0]["cached"]
    await value.execute(call, context(user_id=43, recalled=[{"id": 10, "content": "喜欢蓝色"}]))
    changed = context(recalled=[{"id": 11, "content": "现在喜欢红色"}])
    await value.execute(call, changed)
    assert changed["memoryIds"] == {11} and value.invoke.await_count == 3


async def test_cache_failure_does_not_fail_a_successful_query(monkeypatch):
    value, _, redis = executor(monkeypatch)
    redis.get.side_effect = redis.set.side_effect = ConnectionError("offline")
    result = await value.execute({"id": "a", "name": "read_post", "args": {"post_id": 12}}, context())
    assert result["success"] and result["facts"] == ["A事实"]


async def test_invalid_cached_result_is_replaced_and_unknown_memory_is_not_saved(monkeypatch):
    value, stored, redis = executor(monkeypatch)
    call = {"id": "a", "name": "read_post", "args": {"post_id": 12}}
    current = context()
    stored[value.cache_key(call["name"], call["args"], current["request"])] = "broken JSON"
    assert (await value.execute(call, current))["success"]
    value.invoke.return_value = {"facts": ["不存在的记忆"], "memoryIds": [999]}
    result = await value.execute(call, context(user_id=43))
    assert not result["success"] and redis.set.await_count == 1


async def test_zero_ttl_disables_cache_reads_and_writes(monkeypatch):
    value, _, redis = executor(monkeypatch, tool_cache_seconds={"read_post": 0})
    call = {"id": "a", "name": "read_post", "args": {"post_id": 12}}
    await value.execute(call, context())
    await value.execute(call, context())
    assert value.invoke.await_count == 2
    redis.get.assert_not_awaited()
    redis.set.assert_not_awaited()


def test_combined_memory_text_is_preserved_and_cache_configuration_rejects_unknown_tools(monkeypatch):
    combined = "记忆" * 600
    assert clean_result({"speechFacts": [combined]})["speechFacts"] == [combined]
    with pytest.raises(ValueError, match="未知工具"):
        executor(monkeypatch, tool_cache_seconds={"unknown": 30})
