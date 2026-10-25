import math


class EmbeddingClient:
    def __init__(self, settings, http, gateway):
        self.settings, self.http, self.gateway = settings, http, gateway

    async def encode(self, texts, text_type):
        if text_type not in {"document", "query"}:
            raise ValueError("向量必须指定 document 或 query")

        async def call():
            response = await self.http.post(
                self.settings.embedding_url,
                headers=self.gateway.headers(),
                timeout=3,
                json={
                    "model": self.settings.embedding_model,
                    "input": {"texts": texts},
                    "parameters": {"text_type": text_type, "dimension": 1024},
                },
            )
            response.raise_for_status()
            return response.json()

        payload = await self.gateway.invoke(
            self.settings.embedding_model,
            "embedding",
            sum(map(len, texts)),
            call,
            lambda r: {"input_tokens": r.get("usage", {}).get("total_tokens", 0)},
        )
        rows = sorted(payload["output"]["embeddings"], key=lambda item: item["text_index"])
        if [row["text_index"] for row in rows] != list(range(len(texts))):
            raise ValueError("向量编号错误")
        vectors = [row["embedding"] for row in rows]
        if len(vectors) != len(texts) or any(
            len(vector) != 1024 or any(not math.isfinite(value) for value in vector) for vector in vectors
        ):
            raise ValueError("向量维度或数量错误")
        return vectors
