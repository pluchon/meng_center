from __future__ import annotations

import argparse
import asyncio
import json
import logging
from pathlib import Path

import httpx
from redis.asyncio import Redis

from app.clients.models import ModelFactory
from app.config.settings import Settings
from app.graphs.moderation import confirm_content
from app.runtime.tasks.structured import TaskRuntime
from app.schemas.models import Moderation, Understanding
from evals.companion.budget import EvaluationGateway, claim_run, evaluation_settings, save_checkpoint

LOG = logging.getLogger(__name__)


async def evaluate(args) -> None:
    baseline = args.baseline
    settings, key = evaluation_settings(Settings(), args)
    settings = settings.model_copy(update={"concurrency": 1})
    redis = Redis(
        host=settings.redis_host,
        port=settings.redis_port,
        password=settings.redis_password.get_secret_value(),
        decode_responses=True,
    )
    http = httpx.AsyncClient()
    gateway = EvaluationGateway(settings, redis, key, args.token_budget)
    if await asyncio.to_thread(args.output.exists):
        await redis.aclose()
        await http.aclose()
        raise ValueError("已有站内评测报告，不能覆盖或重新生成")
    models = ModelFactory(settings, redis, http, gateway)
    models.task_runtime = TaskRuntime(models)
    chains = {name: models.task_runtime.chain(name) for name in ("moderate_text", "understand")}
    correct = blocked = caught = false_positive = normal = topic_hits = topic_total = total = 0
    reviews = overturned = 0
    records = []

    async def checkpoint(complete=False, **extra):
        await save_checkpoint(
            args.output,
            dict(
                usage=await gateway.usage_summary(),
                complete=complete,
                budgetKey=key,
                records=records,
                **extra,
            ),
        )

    try:
        if not args.phase_id:
            raise ValueError("站内评测需要本期固定 phase-id")
        args.smoke, args.resume = False, False
        await claim_run(redis, args, key)
        await checkpoint()
        for file in (Path(__file__).parent / "moderation.jsonl", Path(__file__).parent / "topics.jsonl"):
            content = await asyncio.to_thread(file.read_text, encoding="utf-8")
            for line in content.splitlines():
                row = json.loads(line)
                schema = Moderation if "verdict" in row else Understanding
                task = "moderate_text" if schema is Moderation else "understand"
                chain = chains[task]
                async with models.usage() as callback:
                    result = await chain.ainvoke(
                        {"payload": row["input"]},
                        config={"callbacks": [callback], "run_name": "local-evaluation", "tags": ["eval"]},
                    )
                    initial = result.verdict
                    if initial == "BLOCK":
                        reason = await confirm_content(models, row["input"], callback)
                        if reason:
                            result = result.model_copy(update={"verdict": "SUSPECT", "reason": reason})
                reviews += initial == "BLOCK"
                overturned += initial == "BLOCK" and result.verdict != "BLOCK"
                records.append(
                    dict(
                        task=task, expected=row, initialVerdict=initial, result=result.model_dump(mode="json")
                    )
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
                await checkpoint()
                print(f"station task={task} sample={total + topic_total}", flush=True)
        metrics = {
            "accuracy": correct / total,
            "blockRecall": caught / max(1, blocked),
            "falsePositiveRate": false_positive / max(1, normal),
            "topicHitRate": topic_hits / topic_total,
            "moderationSamples": total,
            "topicSamples": topic_total,
            "blockReviews": reviews,
            "blockOverturnRate": overturned / max(1, reviews),
        }
        await checkpoint(complete=True, **metrics)
        LOG.info("evaluation=%s", json.dumps(metrics, ensure_ascii=False))
        if baseline:
            previous = json.loads(await asyncio.to_thread(baseline.read_text, encoding="utf-8"))
            if (
                metrics["blockRecall"] < previous["blockRecall"]
                or metrics["falsePositiveRate"] > previous["falsePositiveRate"]
            ):
                raise SystemExit("Evaluation regression: do not merge")
    except Exception as error:
        await checkpoint(stopReason=type(error).__name__)
        raise
    finally:
        await redis.aclose()
        await http.aclose()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    logging.getLogger("httpx").setLevel(logging.WARNING)
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--output", type=Path, default=Path("../test-output/ai-evaluation.json"))
    parser.add_argument("--token-budget", type=int, required=True)
    parser.add_argument("--budget-id", required=True)
    parser.add_argument("--acceptance", action="store_true")
    parser.add_argument("--phase-id", required=True)
    asyncio.run(evaluate(parser.parse_args()))
