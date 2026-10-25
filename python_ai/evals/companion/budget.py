import re
from contextvars import ContextVar

from app.runtime.models.gateway import ModelGateway


class EvaluationLimit(Exception):
    pass


class EvaluationUnavailable(Exception):
    pass


def evaluation_settings(settings, args):
    if not args.token_budget or args.token_budget <= 0:
        raise ValueError("评测必须显式指定已授权的独立 token 预算")
    if not args.budget_id or not re.fullmatch(r"[a-zA-Z0-9._-]{1,100}", args.budget_id):
        raise ValueError("评测必须指定固定授权编号")
    return settings, "meng:ai:evaluation:" + args.budget_id


class EvaluationGateway(ModelGateway):
    def __init__(self, settings, redis, key, limit):
        super().__init__(settings, redis)
        self.key, self.limit = key, limit
        self.exhausted = False
        self.infrastructure_error = None
        self.reserved = ContextVar("evaluation_reservation", default=0)

    async def acquire(self, model, estimate):
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

    async def record(self, model, purpose, usage, elapsed):
        reserved = self.reserved.get()
        actual = usage.get("input_tokens", 0) + usage.get("output_tokens", 0)
        if "input_tokens" in usage:
            await self.redis.incrby(self.key, actual - reserved)
        self.reserved.set(0)
        await super().record(model, purpose, usage, elapsed)

    async def invoke(self, model, purpose, estimate, call, usage, retry=True):
        try:
            return await super().invoke(model, purpose, estimate, call, usage, retry)
        except Exception as error:
            status = getattr(error, "status_code", 0)
            if status in (401, 402, 403, 404):
                self.infrastructure_error = dict(model=model, purpose=purpose, status=status)
            raise

    def check_available(self):
        if self.exhausted:
            raise EvaluationLimit("评测额度已用完，保留此前检查点")
        if self.infrastructure_error:
            raise EvaluationUnavailable(
                "供应商拒绝模型调用，保留此前检查点：" + str(self.infrastructure_error)
            )
