import json
from unittest.mock import AsyncMock

import httpx
import pytest
from pydantic import SecretStr

from app.clients.embedding import EmbeddingClient
from app.clients.rerank import RerankClient
from app.config.settings import Settings
from app.runtime.models.gateway import ModelGateway, ModelUnavailable


@pytest.mark.parametrize(
    "rows,expected",
    [
        ([], []),
        ([{"index": 1, "relevance_score": 0.9}, {"index": 0, "relevance_score": 0.1}], [1]),
        ([{"index": 3, "relevance_score": 0.9}], [0, 1]),
    ],
)
async def test_rerank_threshold_empty_results_and_invalid_index_fallback(rows, expected):
    settings = Settings.model_construct(api_key=SecretStr("fixture"))
    redis = AsyncMock()
    redis.exists.return_value = False
    redis.eval.return_value = 1
    async with httpx.AsyncClient(
        transport=httpx.MockTransport(lambda r: httpx.Response(200, json={"output": {"results": rows}}))
    ) as http:
        result = await RerankClient(settings, http, ModelGateway(settings, redis)).rank("问题", ["一", "二"])
    assert result["indexes"] == expected


@pytest.mark.parametrize("text_type", ["document", "query"])
async def test_native_embeddings_preserve_explicit_encoding_and_order(text_type):
    settings = Settings.model_construct(api_key=SecretStr("fixture"))
    redis = AsyncMock()
    redis.exists.return_value = False
    redis.eval.return_value = 1

    def response(request):
        payload = json.loads(request.content)
        assert payload["parameters"] == {"text_type": text_type, "dimension": 1024}
        assert payload["input"]["texts"] == ["甲", "乙"]
        return httpx.Response(
            200,
            json={
                "output": {
                    "embeddings": [
                        {"text_index": 1, "embedding": [2.0] * 1024},
                        {"text_index": 0, "embedding": [1.0] * 1024},
                    ]
                }
            },
        )

    async with httpx.AsyncClient(transport=httpx.MockTransport(response)) as http:
        result = await EmbeddingClient(settings, http, ModelGateway(settings, redis)).encode(
            ["甲", "乙"], text_type
        )
    assert result[0][0] == 1.0 and result[1][0] == 2.0


async def test_unavailable_recall_keeps_three_candidates_and_does_not_repeat_gateway_retries():
    from app.runtime.models.retry import transient

    settings = Settings.model_construct(api_key=SecretStr("fixture"))
    redis = AsyncMock()
    redis.exists.return_value = False
    redis.eval.return_value = 1
    redis.incr.return_value = 1
    calls = []

    def unavailable(request):
        calls.append(request)
        raise httpx.ConnectError("offline", request=request)

    async with httpx.AsyncClient(transport=httpx.MockTransport(unavailable)) as http:
        gateway = ModelGateway(settings, redis)
        result = await RerankClient(settings, http, gateway).rank("问题", list("甲乙丙丁戊"))
        assert result == {"indexes": [0, 1, 2], "scores": [], "reranked": False}
        assert len(calls) == 2
        with pytest.raises(ModelUnavailable) as stopped:
            await EmbeddingClient(settings, http, gateway).encode(["甲"], "query")
        assert not transient(stopped.value)
        assert len(calls) == 4
