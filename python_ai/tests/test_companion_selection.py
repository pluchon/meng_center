from unittest.mock import AsyncMock, MagicMock

import pytest

from app.clients.model_gateway import BudgetExceeded, ModelGateway
from app.config.settings import Settings
from evals.companion.select import choose


async def test_evaluation_budget_reserves_atomically_without_using_business_budget():
    redis = AsyncMock()
    redis.get.return_value = "10"
    redis.exists.return_value = 0
    redis.eval.side_effect = [1, 0]
    gateway = ModelGateway(Settings.model_construct(), redis, "meng:ai:evaluation:fixture")
    with pytest.raises(BudgetExceeded):
        await gateway.acquire("fixture", 100)
    assert redis.get.await_args.args[0] == "meng:ai:evaluation:fixture"
    assert redis.eval.await_count == 2
    assert redis.eval.await_args.args[-3:] == (100, gateway.settings.daily_token_limit, "fixed")


async def test_evaluation_accounting_refunds_unused_reservation_only_in_its_own_budget():
    redis = AsyncMock()
    pipe = MagicMock()
    pipe.__aenter__.return_value = pipe
    pipe.incrby.return_value = pipe
    pipe.expire.return_value = pipe
    pipe.execute = AsyncMock()
    redis.pipeline = MagicMock(return_value=pipe)
    gateway = ModelGateway(Settings.model_construct(), redis, "meng:ai:evaluation:fixture")
    await gateway.record("fixture", "companion", {"input_tokens": 60, "output_tokens": 10}, 0.1, reserved=100)
    pipe.incrby.assert_called_once_with(gateway.daily_key(), -30)
    pipe.expire.assert_not_called()


async def test_business_accounting_settles_the_original_day_and_keeps_unknown_usage_reserved():
    redis = AsyncMock()
    pipe = MagicMock()
    pipe.__aenter__.return_value = pipe
    pipe.incrby.return_value = pipe
    pipe.expire.return_value = pipe
    pipe.execute = AsyncMock()
    redis.pipeline = MagicMock(return_value=pipe)
    gateway = ModelGateway(Settings.model_construct(), redis)
    await gateway.record("fixture", "companion", {}, 0.1, reserved=100, budget_key="ai:tokens:previous-day")
    pipe.incrby.assert_called_once_with("ai:tokens:previous-day", 0)
    pipe.expire.assert_called_once_with("ai:tokens:previous-day", 172800)


def test_selection_uses_matched_pairs_and_rejects_a_model_with_runtime_failures():
    summary = {
        name: {"humanTotal": 6, "humanPassed": 6, "safetyTotal": 4, "safetyPassed": 4, "blindCount": 6}
        for name in ("baseline", "candidate")
    }
    pairs = [
        {
            "scores": {
                "baseline": {"character": 7, "artificial": 1},
                "candidate": {"character": 8, "artificial": 1},
            }
        }
    ] * 6
    winner, comparisons = choose(summary, pairs, "baseline", 4)
    assert winner == "candidate" and comparisons["candidate"]["margin"] == 1
    summary["candidate"]["humanPassed"] = 5
    assert choose(summary, pairs, "baseline", 4)[0] == "baseline"
