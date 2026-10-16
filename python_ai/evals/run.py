from __future__ import annotations

import argparse
import asyncio
import json
import logging
from pathlib import Path

from redis.asyncio import Redis

from app.chains.structured import build_chain
from app.clients.models import ModelFactory
from app.config.settings import Settings
from app.schemas.models import Moderation, Understanding

LOG = logging.getLogger(__name__)


async def evaluate(baseline: Path | None) -> None:
    settings = Settings()
    redis = Redis(
        host=settings.redis_host,
        port=settings.redis_port,
        password=settings.redis_password.get_secret_value(),
        decode_responses=True,
    )
    models = ModelFactory(settings, redis)
    correct = blocked = caught = false_positive = normal = topic_hits = topic_total = total = 0
    try:
        for file in (Path(__file__).parent / "moderation.jsonl", Path(__file__).parent / "topics.jsonl"):
            content = await asyncio.to_thread(file.read_text, encoding="utf-8")
            for line in content.splitlines():
                row = json.loads(line)
                schema = Moderation if "verdict" in row else Understanding
                task = "moderate" if schema is Moderation else "understand"
                chain = build_chain(models.classifier, schema, task, settings.structured_method)
                async with models.usage() as callback:
                    result = await chain.ainvoke(
                        {"payload": row["input"]},
                        config={"callbacks": [callback], "run_name": "local-evaluation", "tags": ["eval"]},
                    )
                if schema is Moderation:
                    total += 1
                    correct += result.verdict == row["verdict"]
                    blocked += row["verdict"] == "BLOCK"
                    caught += row["verdict"] == "BLOCK" and result.verdict == "BLOCK"
                    normal += row["verdict"] == "PASS"
                    false_positive += row["verdict"] == "PASS" and result.verdict == "BLOCK"
                else:
                    topic_total += 1
                    topic_hits += any(
                        word.casefold() in " ".join(result.topics).casefold()
                        for word in row["expectedTopics"]
                    )
        metrics = {
            "accuracy": correct / total,
            "blockRecall": caught / max(1, blocked),
            "falsePositiveRate": false_positive / max(1, normal),
            "topicHitRate": topic_hits / topic_total,
            "moderationSamples": total,
            "topicSamples": topic_total,
        }
        LOG.info("evaluation=%s", json.dumps(metrics, ensure_ascii=False))
        if baseline:
            previous = json.loads(await asyncio.to_thread(baseline.read_text, encoding="utf-8"))
            if (
                metrics["blockRecall"] < previous["blockRecall"]
                or metrics["falsePositiveRate"] > previous["falsePositiveRate"]
            ):
                raise SystemExit("Evaluation regression: do not merge")
    finally:
        await redis.aclose()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    logging.getLogger("httpx").setLevel(logging.WARNING)
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", type=Path)
    asyncio.run(evaluate(parser.parse_args().baseline))
