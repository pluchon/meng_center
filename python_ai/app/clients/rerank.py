import math


class RerankClient:
    def __init__(self, settings, http, gateway):
        self.settings, self.http, self.gateway = settings, http, gateway

    async def rank(self, query, documents, limit=5, scene="recall"):
        if not documents:
            return {"indexes": [], "scores": [], "reranked": True}
        if len(documents) > 20 or not 1 <= limit <= 20:
            raise ValueError("检索候选数量不合法")

        async def call():
            response = await self.http.post(
                self.settings.rerank_url,
                headers=self.gateway.headers(),
                timeout=3,
                json={
                    "model": self.settings.rerank_model,
                    "input": {"query": query, "documents": documents},
                    "parameters": {
                        "top_n": min(limit, len(documents)),
                        "instruct": self.settings.rerank_instructions.get(
                            scene, "Rank passages that directly answer the query."
                        ),
                    },
                },
            )
            response.raise_for_status()
            return response.json()

        try:
            data = await self.gateway.invoke(
                self.settings.rerank_model,
                "rerank",
                sum(map(len, documents)),
                call,
                lambda r: {"input_tokens": r.get("usage", {}).get("total_tokens", 0)},
            )
            seen, ranked = set(), []
            for row in data["output"]["results"]:
                index, score = row["index"], float(row["relevance_score"])
                if isinstance(index, bool) or not isinstance(index, int) or not 0 <= index < len(documents):
                    raise ValueError("重排下标越界")
                if index in seen or not math.isfinite(score) or not 0 <= score <= 1:
                    raise ValueError("重排分数或下标错误")
                seen.add(index)
                if score >= self.settings.rerank_thresholds.get(scene, 0.3):
                    ranked.append((index, score))
            ranked.sort(key=lambda row: row[1], reverse=True)
            return {
                "indexes": [r[0] for r in ranked[:limit]],
                "scores": [r[1] for r in ranked[:limit]],
                "reranked": True,
            }
        except Exception:
            return {"indexes": list(range(min(limit, len(documents)))), "scores": [], "reranked": False}
