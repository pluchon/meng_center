from __future__ import annotations

import re


def evaluation_settings(settings, args):
    if not args.token_budget or args.token_budget <= 0:
        raise ValueError("评测必须显式指定已授权的独立 token 预算")
    if not args.budget_id or not re.fullmatch(r"[a-zA-Z0-9._-]{1,100}", args.budget_id):
        raise ValueError("评测必须指定固定预算编号；重复执行使用同一编号累计")
    return settings.model_copy(update={"daily_token_limit": args.token_budget}), (
        "meng:ai:evaluation:" + args.budget_id
    )
