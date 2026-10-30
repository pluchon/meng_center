import asyncio
import json
import re
from contextvars import ContextVar
from pathlib import Path

from app.runtime.models.gateway import ModelGateway


async def save_checkpoint(path, report):
    def write():
        output = Path(path)
        output.parent.mkdir(parents=True, exist_ok=True)
        temporary = output.with_suffix(output.suffix + ".tmp")
        temporary.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        temporary.replace(output)

    await asyncio.to_thread(write)


class EvaluationLimit(Exception):
    pass


class EvaluationUnavailable(Exception):
    pass


def evaluation_settings(settings, args):
    if not args.token_budget or args.token_budget <= 0:
        raise ValueError("评测必须显式指定已授权的独立 token 预算")
    if not args.budget_id or not re.fullmatch(r"[a-zA-Z0-9._-]{1,100}", args.budget_id):
        raise ValueError("评测必须指定固定授权编号")
    if not getattr(args, "smoke", False) and not getattr(args, "acceptance", False):
        raise ValueError("批量评测必须由仓库主人明确验收后启用acceptance")
    return settings, "meng:ai:evaluation:" + args.budget_id


def validate_mode(args):
    if not args.smoke and getattr(args, "smoke_kind", "plain") != "plain":
        raise ValueError("冒烟种类只允许用于smoke")
    if getattr(args, "continue_budget_stop", False) and not (args.smoke and args.resume):
        raise ValueError("追加预算只允许续接已经因额度停止的冒烟")
    if not re.fullmatch(r"[a-zA-Z0-9._-]{1,100}", args.phase_id or ""):
        raise ValueError("必须指定本期固定 phase-id，不能通过更换授权编号重置次数")
    if args.smoke:
        if (
            args.acceptance
            or args.compare_model
            or args.primary_model
            or args.small_sample
            or args.restart_blind
        ):
            raise ValueError("冒烟只允许默认说话模型，不允许对照、选型或盲评")
        if not args.character:
            raise ValueError("冒烟必须指定一个角色")
        if not 0 < args.token_budget <= 30_000:
            raise ValueError("冒烟 token 上限为30000")
    elif not args.acceptance or args.small_sample or args.character:
        raise ValueError("完整评测需要明确验收，固定奶糖35轮；开发期使用smoke")


async def claim_run(redis, args, key):
    """按开发期登记次数；更换预算编号也不能重置，续接只能使用同一报告。"""
    from pathlib import Path

    from app.runtime.context.cache import digest

    if not re.fullmatch(r"[a-zA-Z0-9._-]{1,100}", args.phase_id or ""):
        raise ValueError("评测必须指定有效的本期固定 phase-id")
    mode = "smoke" if args.smoke else "acceptance"
    path = await asyncio.to_thread(Path(args.output).resolve)
    ident = digest([str(path), key])
    allowed = await redis.eval(
        "local old=redis.call('HGET',KEYS[1],ARGV[1]); "
        "if old then return tonumber(ARGV[3]) end; "
        "if tonumber(ARGV[3])==1 then return 0 end; "
        "local grant=redis.call('HGET',KEYS[2],ARGV[1]); "
        "if redis.call('HLEN',KEYS[1])>=tonumber(ARGV[2]) and not grant then return 0 end; "
        "redis.call('HSET',KEYS[1],ARGV[1],grant or '1'); "
        "if grant then redis.call('HDEL',KEYS[2],ARGV[1]) end; return 1",
        2,
        f"meng:ai:evaluation-phase:{args.phase_id}:{mode}",
        f"meng:ai:evaluation-phase:{args.phase_id}:{mode}:authorized-reports",
        ident,
        3 if args.smoke else 1,
        int(args.resume),
    )
    if not allowed:
        raise EvaluationLimit("本期次数已用完、重复启动，或没有可续接的登记")


class EvaluationGateway(ModelGateway):
    def __init__(self, settings, redis, key, limit):
        super().__init__(settings, redis)
        self.key, self.limit = key, limit
        self.exhausted = False
        self.infrastructure_error = None
        self.reserved = ContextVar("evaluation_reservation", default=0)
        self.purpose = ContextVar("evaluation_purpose", default="unknown")
        self.usage_key = key + ":usage"

    async def acquire(self, model, estimate):
        self.check_available()
        await super().acquire(model, estimate)
        allowed = await self.redis.eval(
            "local n=tonumber(redis.call('GET',KEYS[1]) or 0); "
            "if n+tonumber(ARGV[1])>tonumber(ARGV[2]) then return 0 end; "
            "redis.call('INCRBY',KEYS[1],ARGV[1]); return 1",
            1,
            self.key,
            estimate,
            self.limit,
        )
        if not allowed:
            self.exhausted = True
            raise EvaluationLimit("评测额度已用完")
        self.reserved.set(estimate)
        await self.redis.hincrby(self.usage_key, self.purpose.get() + ":calls", 1)

    async def record(self, model, purpose, usage, elapsed):
        reserved = self.reserved.get()
        actual = usage.get("input_tokens", 0) + usage.get("output_tokens", 0)
        if "input_tokens" in usage:
            await self.redis.incrby(self.key, actual - reserved)
        for field in ("input_tokens", "output_tokens"):
            await self.redis.hincrby(self.usage_key, purpose + ":" + field, int(usage.get(field, 0)))
        await self.redis.hincrby(self.usage_key, purpose + ":completed", 1)
        await self.redis.hincrby(self.usage_key, purpose + ":search_calls", int(usage.get("search_calls", 0)))
        if int(await self.redis.get(self.key) or 0) >= self.limit:
            self.exhausted = True
        self.reserved.set(0)
        await super().record(model, purpose, usage, elapsed)

    async def invoke(self, model, purpose, estimate, call, usage, retry=True):
        token = self.purpose.set(purpose)
        try:
            return await super().invoke(model, purpose, estimate, call, usage, retry)
        except Exception as error:
            status = getattr(error, "status_code", 0)
            if status in (401, 402, 403, 404):
                self.infrastructure_error = dict(model=model, purpose=purpose, status=status)
            raise
        finally:
            self.purpose.reset(token)

    async def usage_summary(self):
        fields = await self.redis.hgetall(self.usage_key)
        purposes = {}
        for key, value in fields.items():
            purpose, field = key.split(":", 1)
            purposes.setdefault(purpose, {})[field] = int(value)
        return dict(
            calls=sum(row.get("calls", 0) for row in purposes.values()),
            purposes=purposes,
            confirmedTokens=sum(
                row.get("input_tokens", 0) + row.get("output_tokens", 0) for row in purposes.values()
            ),
            unconfirmedCalls=sum(row.get("calls", 0) - row.get("completed", 0) for row in purposes.values()),
            chargedOrReservedTokens=int(await self.redis.get(self.key) or 0),
            tokenLimit=self.limit,
            searchCalls=sum(row.get("search_calls", 0) for row in purposes.values()),
        )

    def check_available(self):
        if self.exhausted:
            raise EvaluationLimit("评测额度已用完，保留此前检查点")
        if self.infrastructure_error:
            raise EvaluationUnavailable(
                "供应商拒绝模型调用，保留此前检查点：" + str(self.infrastructure_error)
            )
