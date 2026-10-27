"""仅由已获授权的站内验收批次调用，与该批次共用网关、预算和检查点。"""

from app.clients.rerank import RerankClient
from evals.retrieval.calibrate import SCENES, dataset


async def collect(models, archive, checkpoint):
    rows, digest = dataset()
    settings = models.settings.model_copy(update={"rerank_thresholds": dict.fromkeys(SCENES, 0)})
    client = RerankClient(settings, models.http, models.gateway)
    archive.update(format="raw-rerank-v1", model=client.spec.model, datasetSha256=digest, records=[])
    archive["instructions"] = {
        scene: settings.rerank_instructions.get(scene, "Rank passages that directly answer the query.")
        for scene in SCENES
    }
    await checkpoint()
    for row in rows:
        result = await client.rank(row["query"], row["documents"], len(row["documents"]), row["scene"])
        if not result["reranked"] or sorted(result["indexes"]) != list(range(len(row["documents"]))):
            raise ValueError("重排调用失败或原始分数不完整，保留检查点并停止")
        scores = [0.0] * len(row["documents"])
        for index, value in zip(result["indexes"], result["scores"], strict=True):
            scores[index] = value
        archive["records"].append({"id": row["id"], "scores": scores, "reranked": True})
        await checkpoint()
