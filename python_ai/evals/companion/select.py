"""用匿名论坛语料验收合成会话，真实调用必须有独立授权额度。"""

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
    judge_single,
    model_scores,
    validate_candidates,
)
from evals.companion.budget import (
    EvaluationGateway,
    EvaluationUnavailable,
    claim_run,
    evaluation_settings,
    validate_mode,
)
from evals.companion.cards import CARDS, card_payload, configure_catalog, emotion_payload, examples_payload
from evals.companion.cases import QUESTIONS
from evals.companion.corpus import load_corpus
from evals.companion.fixtures import RecordedTools, scene
from evals.companion.metrics import baseline_metrics, compare, quality_failures, reply_passed, summarize
from evals.companion.protocol import comparison_plan, comparison_status, generation_conditions

MODELS = ("deepseek-v4.1-flash-1017", "deepseek-v4-pro")
SCENE = scene(0)
BLIND_PROTOCOL_VERSION = 6


def smoke_image():
    import base64
    from io import BytesIO

    from PIL import Image, ImageDraw

    image = Image.new("RGB", (128, 128), "white")
    ImageDraw.Draw(image).ellipse((24, 24, 104, 104), fill="red")
    output = BytesIO()
    image.save(output, format="JPEG")
    return "data:image/jpeg;base64," + base64.b64encode(output.getvalue()).decode()


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
    return dict(mood=row.get("scene", SCENE), examples=examples_payload(row["card"]))


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


def blind_samples(rows, seed):
    """每个角色固定抽取二十组，恢复时不改变顺序。"""
    selected = []
    for code in CARDS:
        candidates = sorted((row for row in rows if row["card"] == code), key=lambda row: row["id"])
        selected.extend(random.Random(f"{seed}:{code}").sample(candidates, min(20, len(candidates))))
    return selected


def question_at(index):
    return {
        1: "我住在台中，下周要考试。",
        16: "我搬到台北了。",
        28: "我现在住在哪座城市？我说过银行卡密码吗？",
        32: "找几篇社区里的配色帖子。",
        33: "我最近写了哪些帖子？",
        34: "看看我的签到情况。",
    }.get(index, QUESTIONS[index % len(QUESTIONS)])


async def run(args):
    validate_mode(args)
    if args.restart_blind and not args.resume:
        raise ValueError("重新盲评必须同时指定resume和原始output")
    output = Path(args.output)
    if await asyncio.to_thread(output.exists) and not args.resume:
        raise ValueError("报告已存在，只能从检查点续接，不能覆盖或重新生成回复")
    configure_catalog(args.catalog)
    corpus_cases, corpus_metadata = ([], None) if args.smoke else load_corpus()
    previous_baseline, plan = comparison_plan(args.baseline, args.changed_variable, args.output)
    settings, key = evaluation_settings(Settings(), args)
    settings = settings.model_copy(update={"concurrency": 1})
    candidates = [settings.speech_model, *([args.compare_model] if args.compare_model else [])]
    if args.primary_model and args.primary_model != settings.speech_model:
        raise ValueError("主模型必须是当前默认模型，选型请显式指定compare-model")
    validate_candidates(candidates)
    setup = dict(
        promptVersion=VERSION,
        blindProtocolVersion=BLIND_PROTOCOL_VERSION,
        blindJudges=[] if args.smoke else list(JUDGES),
        catalogVersion=card_payload(next(iter(CARDS)))["version"],
        candidates=candidates,
        character=args.character,
        smallSample=False,
        smoke=args.smoke,
        graphProtocol="naitang-stategraph-v1",
        speechProvider="dashscope",
        generationProtocolVersion=4,
        toolFixtureVersion=4,
        sceneProtocolVersion=2,
        scene=SCENE,
        seed=args.seed,
        phaseId=args.phase_id,
        corpus=corpus_metadata,
        generationConditions=generation_conditions(settings),
    )
    plan = comparison_status(plan, setup, previous_baseline)
    records, pairs = {}, []
    retry_id, continuations = None, []
    pending = None
    if args.resume:
        previous = json.loads(await asyncio.to_thread(output.read_text, encoding="utf-8"))
        records, pairs = resume_records(previous, setup, key, args.restart_blind)
        pending = previous.get("pendingTurn")
        if previous.get("complete") and not args.restart_blind:
            raise ValueError("评测已经完成，禁止重复执行")
        if pending:
            raise ValueError("上次请求中断且没有完整回复，保留检查点，不自动重新生成")
        continuations = previous.get("continuations", [])
        if args.continue_budget_stop:
            rows = records.get(settings.speech_model, [])
            if (
                previous.get("stoppedReason") != "EvaluationLimit"
                or not rows
                or rows[-1]["result"] is not None
            ):
                raise ValueError("追加预算只能续接额度中断且没有生成回复的末轮")
            retry_id = rows[-1]["id"]
            continuations = [
                *continuations,
                dict(
                    stoppedReason=previous["stoppedReason"],
                    usage=previous["usage"],
                    retryId=retry_id,
                ),
            ]
    redis = Redis(
        host=settings.redis_host,
        port=settings.redis_port,
        password=settings.redis_password.get_secret_value(),
        decode_responses=True,
    )
    gateway = EvaluationGateway(settings, redis, key, args.token_budget)
    invocation_start = int(await redis.get(key) or 0)
    if args.continue_budget_stop:
        # 本次新增额度仍受冒烟上限约束，历史扣减与未知用量的预留均不清零。
        gateway.limit = invocation_start + args.token_budget
    output_path = await asyncio.to_thread(output.resolve)
    gateway.usage_key = key + ":usage:" + digest(str(output_path))

    async def checkpoint(complete=False, **summary):
        output.parent.mkdir(parents=True, exist_ok=True)
        usage = await gateway.usage_summary()
        usage.update(
            invocationTokenLimit=args.token_budget,
            invocationChargedOrReservedTokens=usage["chargedOrReservedTokens"] - invocation_start,
        )
        report = dict(
            usage=usage,
            complete=complete,
            setup=setup,
            records=records,
            pairs=pairs,
            pendingTurn=pending,
            budgetKey=key,
            comparisonProtocol=plan,
            catalog=cards.CATALOG,
            continuations=continuations,
            **summary,
        )
        temporary = output.with_suffix(output.suffix + ".tmp")
        temporary.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        temporary.replace(output)

    try:
        await claim_run(redis, args, key)
        print(
            f"budget={args.token_budget} models={candidates} rounds={5 if args.smoke else 35} "
            f"blindGroups={0 if args.smoke else 20}",
            flush=True,
        )
        async with httpx.AsyncClient() as http:
            for candidate in candidates:
                configured = settings.model_copy(
                    update={
                        "speech_model": candidate,
                        "model_overrides": {**settings.model_overrides, "speech": {"fallbacks": ()}},
                    }
                )
                models = ModelFactory(configured, redis, http, gateway)
                service = Companion(
                    Assistant(models, CoverImageClient(configured, http, gateway)), http
                )

                async def catalog():
                    return emotion_payload()

                service.emotions = catalog
                service.executor = RecordedTools()
                records.setdefault(candidate, [])
                completed = {row["id"]: row for row in records[candidate]}
                for code in CARDS:
                    if args.character and code != args.character:
                        continue
                    history = []
                    for index in range(5 if args.smoke else 35):
                        case = None if args.smoke else corpus_cases[index]
                        ident = f"{code}-{index}"
                        question = (
                            case["text"]
                            if case
                            else "这张图片里是什么颜色的形状？"
                            if index == 4
                            else question_at(index)
                        )
                        row = completed.get(ident)
                        prior_attempts = []
                        if ident == retry_id:
                            prior_attempts = [
                                *row.get("attempts", []),
                                {k: v for k, v in row.items() if k != "attempts"},
                            ]
                            row = None
                        if row is None:
                            pending = dict(model=candidate, id=ident)
                            await checkpoint()
                            body = ReplyRequest(
                                user_id=99999999001,
                                session_id=list(CARDS).index(code) + 1,
                                turn_id=ident,
                                character=code,
                                nickname="虚构测试用户",
                                content=question,
                                images=[smoke_image()] if args.smoke and index == 4 else [],
                                card=card_payload(code),
                                examples=examples_payload(code),
                                mood=scene(index, case["clock"] if case else None),
                                history=history,
                            )
                            random.seed(digest([args.seed, ident]))
                            events = [event async for event in service.stream(body)]
                            result = next((e["data"] for e in events if e["event"] == "result"), None)
                            # 本批真实语料都是闲聊与倾诉；固定语料预期只用于评测，不进入业务路由。
                            decision_issues = (
                                ["闲聊用例触发了未请求的工具"]
                                if case and result and result.get("toolUsage")
                                else []
                            )
                            passed = reply_passed(result, candidate) and not decision_issues
                            row = dict(
                                id=ident,
                                input=question,
                                card=code,
                                scene=body.mood,
                                passed=passed,
                                result=result,
                                decisionIssues=decision_issues,
                            )
                            if case:
                                row["provenance"] = {
                                    key: case[key]
                                    for key in (
                                        "id",
                                        "sourceId",
                                        "site",
                                        "language",
                                        "adaptation",
                                        "category",
                                    )
                                }
                            if prior_attempts:
                                row["attempts"] = prior_attempts
                                records[candidate][-1] = row
                            else:
                                records[candidate].append(row)
                            pending = None
                            await checkpoint()
                            gateway.check_available()
                            agent_calls = [
                                call for call in (result or {}).get("metrics", {}).get("calls", [])
                                if call["purpose"] == "agent"
                            ]
                            cache_created = sum(call.get("cacheCreationTokens", 0) for call in agent_calls)
                            print(
                                f"dialogue model={candidate} character={code} "
                                f"round={index + 1} passed={passed} "
                                f"agentCached={sum(call['cached'] for call in agent_calls)} "
                                f"agentInput={sum(call['input'] for call in agent_calls)} "
                                f"agentCacheCreated={cache_created}",
                                flush=True,
                            )
                        if not row["passed"]:
                            raise EvaluationUnavailable("本轮未通过检查，保留回复并停止，不自动重新生成")
                        history += [
                            Turn(role="USER", content=question),
                            Turn(role="CHARACTER", content="\n".join(row["result"]["bubbles"])),
                        ]
            metrics = summarize(records, card_payload) if not args.smoke else {}
            failures = quality_failures(metrics)
            if failures:
                await checkpoint(stoppedReason="MetricsNotMet", metrics=metrics, qualityFailures=failures)
                return 1
            if not args.smoke:
                judges = {}
                for name in JUDGES:
                    configured = settings.model_copy(
                        update={
                            "judge_model": name,
                            "model_overrides": {**settings.model_overrides, "judge": {"fallbacks": ()}},
                        }
                    )
                    judges[name] = BlindJudge(ModelFactory(configured, redis, http, gateway))
                first_name = candidates[0]
                samples = blind_samples(records[first_name], args.seed)
                other_name = candidates[-1] if len(candidates) == 2 else None
                other_rows = records.get(other_name, []) if other_name else []
                if not other_name and previous_baseline:
                    other_name = "previous:" + first_name
                    other_rows = previous_baseline.get("records", {}).get(first_name, [])
                other = {row["id"]: row for row in other_rows}
                indexed = {pair["sampleId"]: pair for pair in pairs}
                for row in samples:
                    pair = indexed.get(row["id"])
                    if pair is None:
                        pair = dict(sampleId=row["id"], judges={})
                        pairs.append(pair)
                    payload = dict(
                        character=card_payload(row["card"]), scene=blind_context(row), input=row["input"]
                    )
                    reference = other.get(row["id"])
                    paired = bool(
                        reference and reference.get("result") and reference["input"] == row["input"]
                    )
                    pair["mode"] = "paired" if paired else "absolute"
                    for name, judge in judges.items():
                        progress = pair["judges"].setdefault(name, {})
                        if paired:
                            await judge_both_orders(
                                judge,
                                payload,
                                [first_name, other_name],
                                {first_name: row, other_name: reference},
                                {
                                    first_name: history_before(records[first_name], row),
                                    other_name: history_before(other_rows, reference),
                                },
                                progress,
                                checkpoint,
                            )
                        else:
                            await judge_single(
                                judge,
                                payload,
                                first_name,
                                row,
                                history_before(records[first_name], row),
                                progress,
                                checkpoint,
                            )
                        gateway.check_available()
                    await checkpoint()
            descriptive = (
                compare(metrics, baseline_metrics(previous_baseline, card_payload))
                if previous_baseline
                else {}
            )
            reliability = agreement(pairs)
            scores = model_scores(pairs, reliability) if args.compare_model and plan["canConclude"] else {}
            winner = max(scores, key=scores.get) if scores else None
            if winner and scores.get(candidates[0]) == scores[winner]:
                winner = candidates[0]
            await checkpoint(
                complete=True,
                metrics=metrics,
                winner=winner,
                margins=scores,
                comparison=descriptive if plan["canConclude"] and not args.smoke else {},
                descriptiveComparison=descriptive,
                judgeAgreement=reliability,
            )
            print(json.dumps(dict(usage=await gateway.usage_summary()), ensure_ascii=False), flush=True)
            return 0
    except Exception as error:
        await checkpoint(stoppedReason=type(error).__name__, infrastructureError=gateway.infrastructure_error)
        raise
    finally:
        await redis.aclose()


def parser():
    result = argparse.ArgumentParser()
    result.add_argument("--token-budget", type=int, required=True)
    result.add_argument("--budget-id", required=True)
    result.add_argument("--small-sample", action="store_true", help="旧参数，拒绝扩展开发期调用")
    result.add_argument("--acceptance", action="store_true", help="仅在仓库主人明确要求验收后指定")
    result.add_argument("--phase-id", required=True, help="本期固定编号，限制三次冒烟和一次验收")
    result.add_argument(
        "--smoke", action="store_true", help="单个角色、默认模型五轮，不盲评，上限30000 token"
    )
    result.add_argument("--resume", action="store_true", help="从output继续，保留已完成轮次与原授权累计用量")
    result.add_argument(
        "--continue-budget-stop",
        action="store_true",
        help="仅在明确追加预算后续接额度中断的冒烟，token-budget限定本次新增额度",
    )
    result.add_argument(
        "--restart-blind", action="store_true", help="保持回复生成条件与原始回复，仅重做全部盲评"
    )
    result.add_argument("--compare-model")
    result.add_argument("--primary-model", choices=MODELS, help="兼容参数，只接受当前默认说话模型")
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
