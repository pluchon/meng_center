"""固定语料的可重复统计；旧版报告也按同一口径计算。"""

from statistics import median

from app.runtime.speech.signals import signals


def reply_passed(result, model):
    # 与运行时一致：重说后仍有无依据说法或人机味偏高时照发并记录（flaggedReplies），不中断验收；
    # 兜底台词、不计次和换了模型的回复才算失败。
    return bool(result and result["billable"] and result["model"] == model)


def flagged(result):
    metrics = (result or {}).get("metrics", {})
    return bool(result and result.get("billable") and (
        metrics.get("unsupported", 0) > 0 or metrics.get("artificial", 0) >= 7
    ))


def quality_failures(metrics):
    """已有目标未达标时结束本次验收，不继续消耗盲评额度。"""
    failures = []
    for model, values in metrics.items():
        latency = values["firstBubbleP50Ms"]
        if latency is None or latency > 3000:
            failures.append(dict(model=model, metric="firstBubbleP50Ms", value=latency, target=3000))
        for purpose, cache in values.get("cache", {}).items():
            if cache["ratio"] is None or cache["ratio"] < cache["target"]:
                failures.append(dict(
                    model=model, metric="cacheHitRatio", purpose=purpose,
                    value=cache["ratio"], target=cache["target"],
                ))
        for code, character in values["characters"].items():
            if character["kaomojiRatio"] <= 0:
                failures.append(dict(model=model, character=code, metric="kaomojiRatio", value=0))
            for tic, measured in character["tics"].items():
                if measured["frequency"] > measured["target"]:
                    failures.append(
                        dict(model=model, character=code, metric="ticFrequency", tic=tic, **measured)
                    )
    return failures


def cache_summary(rows):
    """按实际输入 token 加权；嘴限定连续十轮的第二至十轮，判断侧统计全部轮次。"""
    result = {}
    for purpose in ("speech", "agent", "judge"):
        selected = rows[1:10] if purpose == "speech" else rows
        calls = [
            call for row in selected
            for call in (row.get("result") or {}).get("metrics", {}).get("calls", [])
            if call["purpose"] == purpose
        ]
        inputs = sum(call.get("input", 0) for call in calls)
        cached = sum(call.get("cached", 0) for call in calls)
        result[purpose] = dict(
            calls=len(calls), inputTokens=inputs, cachedTokens=cached,
            ratio=cached / inputs if inputs else None,
            target=0.7 if purpose == "speech" else 0.6,
        )
    return result


def summarize(records, card_loader):
    result = {}
    for model, rows in records.items():
        normal = [row for row in rows if not row["id"].startswith("safety-")]
        latencies, outputs, adopted, started = [], {"agent": [], "judge": []}, 0, 0
        for row in normal:
            reply = row.get("result") or {}
            metrics = reply.get("metrics", {})
            if (
                reply.get("billable")
                and not reply.get("toolUsage")
                and metrics.get("firstBubbleMs") is not None
            ):
                latencies.append(metrics["firstBubbleMs"])
            started += bool(row.get("result"))
            adopted += bool(metrics.get("speculativeAdopted"))
            for call in metrics.get("calls", []):
                if call["purpose"] in outputs:
                    outputs[call["purpose"]].append(call["output"])
        characters = {}
        for code in sorted({row["card"] for row in normal}):
            replies = [
                "\n".join(row["result"]["bubbles"])
                for row in normal
                if row["card"] == code and row.get("result")
            ]
            counted = signals(replies, card_loader(code))
            characters[code] = dict(
                samples=len(replies),
                tics=counted["tics"],
                kaomojiRatio=counted["kaomojiRatio"],
                repeatedReplyRuns=counted["repeatedReplyRuns"],
                selfImitation=counted["selfImitation"],
            )
        result[model] = dict(
            cases=len(rows),
            passed=sum(row["passed"] for row in rows),
            noToolSamples=len(latencies),
            fallbackCases=sum(
                bool(row.get("result")) and not row["result"].get("billable") for row in normal
            ),
            flaggedReplies=sum(flagged(row.get("result")) for row in normal),
            firstBubbleP50Ms=median(latencies) if latencies else None,
            outputTokens={
                purpose: dict(
                    samples=len(values),
                    p50=median(values) if values else None,
                    mean=sum(values) / len(values) if values else None,
                )
                for purpose, values in outputs.items()
            },
            speculativeStarted=started,
            speculativeAdopted=adopted,
            speculativeAdoptionRatio=adopted / started if started else None,
            cache=cache_summary(normal),
            characters=characters,
        )
    return result


def compare(current, baseline):
    result = {}
    for model, after in current.items():
        baseline_model = model
        before = baseline.get(baseline_model)
        if not before:
            continue
        old, new = before["firstBubbleP50Ms"], after["firstBubbleP50Ms"]
        result[model] = dict(
            baselineModel=baseline_model,
            before=before,
            after=after,
            firstBubbleP50DeltaMs=new - old if old is not None and new is not None else None,
        )
    return result


def baseline_metrics(report, fallback_card_loader):
    records = {
        model: [row for row in rows if row["card"] == "NEKO"] for model, rows in report["records"].items()
    }
    catalog = report.get("catalog")
    loader = fallback_card_loader
    if catalog:
        cards = {row["code"]: row["card"] for row in catalog["characters"]}
        loader = cards.__getitem__
    recomputed = summarize(records, loader)
    # 旧报告的风格统计保留原目标，仅重算奶糖的延迟和调用口径。
    for model, values in report.get("metrics", {}).items():
        if model in recomputed:
            saved = values.get("characters", {}).get("NEKO")
            if saved:
                recomputed[model]["characters"].setdefault("NEKO", {}).update(saved)
    return recomputed
