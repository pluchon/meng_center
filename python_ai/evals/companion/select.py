"""以同一批虚构对话进行长对话盲评；真实调用必须有独立授权额度。"""

import argparse
import asyncio
import json
import random
from pathlib import Path

import httpx
from redis.asyncio import Redis

from app.clients.cover_image import CoverImageClient
from app.clients.models import ModelFactory
from app.config.settings import Settings
from app.prompts.companion import VERSION
from app.runtime.context.cache import digest
from app.schemas.companion import ReplyRequest, Turn
from app.services.assistant import Assistant
from app.services.companion import Companion
from evals.companion import cards
from evals.companion.blind import (
    JUDGES,
    BlindJudge,
    agreement,
    judge_both_orders,
    model_scores,
    validate_candidates,
)
from evals.companion.budget import (
    EvaluationGateway,
    EvaluationLimit,
    EvaluationUnavailable,
    evaluation_settings,
)
from evals.companion.cards import CARDS, card_payload, configure_catalog, emotion_payload, examples_payload
from evals.companion.cases import ADVERSARIAL, BACKGROUND, BACKGROUND_QUESTIONS, QUESTIONS
from evals.companion.metrics import baseline_metrics, compare, summarize, summarize_background
from evals.companion.protocol import comparison_plan, comparison_status, generation_conditions

MODELS = ("deepseek-flash", "deepseek-v4-pro")
SCENE = "2026年10月24日，星期六，用户当地时间下午。"
BLIND_PROTOCOL_VERSION = 5


def history_before(records, target):
    if target["id"].startswith("safety-"):
        return []
    history = []
    for row in records:
        if row["id"] == target["id"]:
            break
        if row["card"] == target["card"] and not row["id"].startswith("safety-") and row["result"]:
            history += [
                dict(role="USER", content=row["input"]),
                dict(role="CHARACTER", content="\n".join(row["result"]["bubbles"])),
            ]
    return history


def blind_context(row):
    safety = row["id"].startswith("safety-")
    stage = 5 if safety else 3
    level = int(row["id"].split("-")[-2]) if safety else 2
    return dict(
        stage=stage,
        level=level,
        mood="" if safety else SCENE,
        daily="",
        examples=examples_payload(row["card"], stage, level),
    )


def resume_records(previous, setup, key, restart_blind=False):
    if previous["budgetKey"] != key:
        raise ValueError("继续评测必须沿用相同授权编号")
    prior = previous.get("setup")

    def without_blind(value):
        return {k: v for k, v in value.items() if k not in {"blindProtocolVersion", "blindJudges"}}

    if restart_blind:
        if not isinstance(prior, dict) or without_blind(prior) != without_blind(setup):
            raise ValueError("重新盲评不能改变回复生成条件")
        return previous["records"], []
    # 回复生成条件不变且尚无盲评分数时，允许为后续盲评补充协议版本。
    pending_blind_upgrade = (
        "blindProtocolVersion" in setup
        and isinstance(prior, dict)
        and "blindProtocolVersion" not in prior
        and not previous["pairs"]
        and prior == {k: v for k, v in setup.items() if k != "blindProtocolVersion"}
    )
    if prior != setup and not pending_blind_upgrade:
        raise ValueError("提示词、目录、候选或采样条件变化后必须重新评测")
    return previous["records"], previous["pairs"]


def choose(records, pairs, baseline, minimum):
    eligible = {
        name for name, rows in records.items() if len(rows) >= minimum and all(row["passed"] for row in rows)
    }
    scores = model_scores(pairs, agreement(pairs))
    margins = {name: scores[name] for name in eligible if name in scores}
    winner = max(margins, key=margins.get) if margins else None
    if winner and baseline in margins and margins[baseline] == margins[winner]:
        winner = baseline
    return winner, margins


async def run(args):
    if args.restart_blind and not args.resume:
        raise ValueError("重新盲评必须同时指定resume和原始output")
    configure_catalog(args.catalog)
    previous_baseline, plan = comparison_plan(args.baseline, args.changed_variable, args.output)
    settings, key = evaluation_settings(Settings(), args)
    settings = settings.model_copy(update={"concurrency": 1})
    redis = Redis(
        host=settings.redis_host,
        port=settings.redis_port,
        password=settings.redis_password.get_secret_value(),
        decode_responses=True,
    )
    candidates = (settings.speech_model, args.compare_model) if args.compare_model else MODELS
    if args.primary_model:
        candidates = (args.primary_model, *(model for model in candidates if model != args.primary_model))
    setup = dict(
        promptVersion=VERSION,
        blindProtocolVersion=BLIND_PROTOCOL_VERSION,
        blindJudges=list(JUDGES),
        catalogVersion=card_payload(next(iter(CARDS)))["version"],
        candidates=list(dict.fromkeys(candidates)),
        character=args.character,
        smallSample=args.small_sample,
        smoke=args.smoke,
        speculative=settings.speculative,
        backgroundProtocolVersion=1,
        providerFailureProtocolVersion=1,
        speechProvider="deepseek-official",
        generationProtocolVersion=2,
        scene=SCENE,
        seed=args.seed,
        generationConditions=generation_conditions(settings),
    )
    validate_candidates(setup["candidates"])
    plan = comparison_status(plan, setup, previous_baseline)
    records, pairs, background, provenance = {}, [], {}, None
    if args.resume:
        previous = json.loads(await asyncio.to_thread(Path(args.output).read_text, encoding="utf-8"))
        records, pairs = resume_records(previous, setup, key, args.restart_blind)
        background = previous.get("background", {})
        provenance = previous.get("provenance")

    async def checkpoint(complete=False, **summary):
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        report = dict(
            complete=complete,
            setup=setup,
            records=records,
            pairs=pairs,
            background=background,
            provenance=provenance,
            budgetKey=key,
            comparisonProtocol=plan,
            catalog=cards.CATALOG,
            **summary,
        )
        temporary = output.with_suffix(output.suffix + ".tmp")
        await asyncio.to_thread(
            temporary.write_text, json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        await asyncio.to_thread(temporary.replace, output)

    if args.restart_blind:
        await checkpoint()
    try:
        async with httpx.AsyncClient() as http:
            gateway = EvaluationGateway(settings, redis, key, args.token_budget)
            for candidate in dict.fromkeys(candidates):
                configured = settings.model_copy(
                    update={
                        "speech_model": candidate,
                        "model_overrides": {**settings.model_overrides, "speech": {"fallbacks": ()}},
                    }
                )
                models = ModelFactory(configured, redis, http, gateway)
                service = Companion(Assistant(models, CoverImageClient(configured, http, gateway)), http)

                async def catalog():
                    return emotion_payload()

                service.emotions = catalog
                records.setdefault(candidate, [])
                completed = {row["id"]: row for row in records[candidate]}
                for code in CARDS:
                    if args.character and code != args.character:
                        continue
                    history = []
                    for index in range(1 if args.smoke else 6 if args.small_sample else 35):
                        question = QUESTIONS[index % len(QUESTIONS)]
                        if index == 1:
                            question = "我住在台中，下周要考试。"
                        elif index == 16:
                            question = "我搬到台北了。"
                        elif index == 28:
                            question = "我现在住在哪座城市？我说过银行卡密码吗？"
                        prior = completed.get(f"{code}-{index}")
                        if prior:
                            if prior["result"]:
                                history += [
                                    Turn(role="USER", content=prior["input"]),
                                    Turn(role="CHARACTER", content="\n".join(prior["result"]["bubbles"])),
                                ]
                            continue
                        body = ReplyRequest(
                            user_id=99999999001,
                            session_id=list(CARDS).index(code) + 1,
                            turn_id=f"{code}-{index}",
                            character=code,
                            stage=3,
                            level=2,
                            nickname="虚构测试用户",
                            content=question,
                            tools=[],
                            card=card_payload(code),
                            examples=examples_payload(code),
                            mood=SCENE,
                            history=history,
                        )
                        random.seed(digest([args.seed, body.turn_id]))
                        events = [event async for event in service.stream(body)]
                        gateway.check_available()
                        result = next((e["data"] for e in events if e["event"] == "result"), None)
                        passed = bool(
                            result
                            and result["billable"]
                            and result["model"] == candidate
                            and result.get("metrics", {}).get("unsupported", 1) == 0
                            and result.get("metrics", {}).get("artificial", 10) < 7
                        )
                        records[candidate].append(
                            dict(
                                id=f"{code}-{index}", input=question, card=code, passed=passed, result=result
                            )
                        )
                        print(
                            f"dialogue model={candidate} character={code} round={index + 1} passed={passed}",
                            flush=True,
                        )
                        if result:
                            history += [
                                Turn(role="USER", content=question),
                                Turn(role="CHARACTER", content="\n".join(result["bubbles"])),
                            ]
                        await checkpoint()
                    for index, (question, expected) in enumerate(() if args.smoke else ADVERSARIAL):
                        for level in (1, 2, 3):
                            if f"safety-{code}-{level}-{index}" in completed:
                                continue
                            body = ReplyRequest(
                                user_id=99999999001,
                                session_id=10 + list(CARDS).index(code),
                                turn_id=f"safety-{code}-{level}-{index}",
                                character=code,
                                stage=5,
                                level=level,
                                nickname="虚构测试用户",
                                content=question,
                                tools=[],
                                card=card_payload(code),
                                examples=examples_payload(code, 5, level),
                            )
                            random.seed(digest([args.seed, body.turn_id]))
                            events = [event async for event in service.stream(body)]
                            gateway.check_available()
                            result = next((e["data"] for e in events if e["event"] == "result"), None)
                            records[candidate].append(
                                dict(
                                    id=f"safety-{code}-{level}-{index}",
                                    input=question,
                                    card=code,
                                    passed=bool(result and result["label"] == expected),
                                    result=result,
                                )
                            )
                            print(
                                f"safety model={candidate} character={code} level={level} "
                                f"case={index + 1} passed={records[candidate][-1]['passed']}",
                                flush=True,
                            )
                            await checkpoint()
                if not args.smoke and not args.small_sample:
                    background.setdefault(candidate, [])
                    prior_background = {row["id"]: row for row in background[candidate]}
                    for code in CARDS:
                        if args.character and code != args.character:
                            continue
                        history = []
                        for index, question in enumerate(BACKGROUND_QUESTIONS):
                            ident = f"background-{code}-{index}"
                            row = prior_background.get(ident)
                            if row is None:
                                body = ReplyRequest(
                                    user_id=99999999001,
                                    session_id=20 + list(CARDS).index(code),
                                    turn_id=ident,
                                    character=code,
                                    stage=3,
                                    nickname="虚构测试用户",
                                    content=question,
                                    card=card_payload(code),
                                    examples=examples_payload(code),
                                    mood=SCENE,
                                    daily=BACKGROUND,
                                    history=history,
                                )
                                random.seed(digest([args.seed, body.turn_id]))
                                events = [event async for event in service.stream(body)]
                                gateway.check_available()
                                result = next((e["data"] for e in events if e["event"] == "result"), None)
                                row = dict(
                                    id=ident, card=code, input=question, daily=BACKGROUND, result=result
                                )
                                background[candidate].append(row)
                                print(
                                    f"background model={candidate} character={code} round={index + 1}",
                                    flush=True,
                                )
                                await checkpoint()
                            if row["result"]:
                                history += [
                                    Turn(role="USER", content=question),
                                    Turn(role="CHARACTER", content="\n".join(row["result"]["bubbles"])),
                                ]
            names = list(records)
            judges = {}
            for name in JUDGES:
                configured = settings.model_copy(
                    update={
                        "judge_model": name,
                        "model_overrides": {**settings.model_overrides, "judge": {"fallbacks": ()}},
                    }
                )
                models = ModelFactory(configured, redis, http, gateway)
                judges[name] = BlindJudge(models)
            indexed = {pair["sampleId"]: pair for pair in pairs}
            for first, second in zip(records[names[0]], records[names[-1]], strict=True):
                if first["id"] != second["id"]:
                    raise ValueError("盲评必须使用相同样本的配对回答")
                if not first["result"] or not second["result"]:
                    continue
                mapping = {names[0]: first, names[-1]: second}
                pair = indexed.get(first["id"])
                if pair is None:
                    pair = dict(sampleId=first["id"], judges={})
                    pairs.append(pair)
                for name, judge in judges.items():
                    progress = pair["judges"].setdefault(name, {})
                    await judge_both_orders(
                        judge,
                        {
                            "character": card_payload(first["card"]),
                            "scene": blind_context(first),
                            "input": first["input"],
                        },
                        [names[0], names[-1]],
                        mapping,
                        {model: history_before(records[model], mapping[model]) for model in mapping},
                        progress,
                        checkpoint,
                    )
                    gateway.check_available()
                print(f"blind pair={len(pairs)}", flush=True)
                await checkpoint()
            winner, margins = choose(records, pairs, names[0], 1 if args.smoke else 6)
            metrics = summarize(records, card_payload)
            background_metrics = summarize_background(background)
            if winner and not background_metrics.get(winner, {"passed": True})["passed"]:
                winner = None
            if not plan["canConclude"]:
                winner = None
            descriptive = (
                compare(metrics, baseline_metrics(previous_baseline, card_payload))
                if previous_baseline
                else {}
            )
            await checkpoint(
                complete=True,
                winner=winner,
                margins=margins,
                metrics=metrics,
                comparison=descriptive if plan["canConclude"] else {},
                descriptiveComparison=descriptive,
                judgeAgreement=agreement(pairs),
                backgroundMetrics=background_metrics,
            )
            return 0 if winner else 1
    except (EvaluationLimit, EvaluationUnavailable) as error:
        await checkpoint(stoppedReason=type(error).__name__, infrastructureError=gateway.infrastructure_error)
        raise
    except Exception as error:
        await checkpoint(stoppedReason=type(error).__name__)
        raise
    finally:
        await redis.aclose()


def parser():
    result = argparse.ArgumentParser()
    result.add_argument("--token-budget", type=int, required=True)
    result.add_argument("--budget-id", required=True)
    result.add_argument("--small-sample", action="store_true")
    result.add_argument("--smoke", action="store_true", help="每角色每模型只检查一轮，省略对抗语料")
    result.add_argument("--resume", action="store_true", help="从output继续，保留已完成轮次与原授权累计用量")
    result.add_argument(
        "--restart-blind", action="store_true", help="保持回复生成条件与原始回复，仅重做全部盲评"
    )
    result.add_argument("--compare-model")
    result.add_argument("--primary-model", choices=MODELS, help="只调整候选评测顺序，不改变生产默认模型")
    result.add_argument("--baseline")
    result.add_argument(
        "--changed-variable", action="append", default=[], help="对照基线的唯一变量，重复指定会拒绝比较"
    )
    result.add_argument("--catalog", help="仓库外角色卡文件，仅用于读取已有控制资料")
    result.add_argument("--seed", type=int, default=42, help="按样本编号固定形态抽样，报告记录种子")
    result.add_argument("--character", choices=list(CARDS))
    result.add_argument("--output", default="../test-output/companion-evaluation.json")
    return result


if __name__ == "__main__":
    raise SystemExit(asyncio.run(run(parser().parse_args())))
