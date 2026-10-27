"""固定语料的可重复统计；旧版报告也按同一口径计算。"""

from statistics import median

from app.runtime.speech.signals import signals


def quality_failures(metrics):
    """已有目标未达标时结束本次验收，不继续消耗盲评额度。"""
    failures = []
    for model, values in metrics.items():
        latency = values["firstBubbleP50Ms"]
        if latency is None or latency > 3000:
            failures.append(dict(model=model, metric="firstBubbleP50Ms", value=latency, target=3000))
        for code, character in values["characters"].items():
            if character["kaomojiRatio"] <= 0:
                failures.append(dict(model=model, character=code, metric="kaomojiRatio", value=0))
            for tic, measured in character["tics"].items():
                if measured["frequency"] > measured["target"]:
                    failures.append(
                        dict(model=model, character=code, metric="ticFrequency", tic=tic, **measured)
                    )
    return failures


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
            started += bool(metrics.get("speculativeStarted"))
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
            counted = signals(replies, card_loader(code), 2)
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
            characters=characters,
        )
    return result


def compare(current, baseline):
    result = {}
    for model, after in current.items():
        baseline_model = model
        if model == "deepseek-flash" and model not in baseline:
            baseline_model = "deepseek-v4.1-flash-1017"
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
    catalog = report.get("catalog")
    if catalog:
        cards = {row["code"]: row["card"] for row in catalog["characters"]}
        return summarize(report["records"], cards.__getitem__)
    # 旧报告缺少目录快照时保留其原始风格统计，不能借新卡改写旧频率目标。
    recomputed = summarize(report["records"], fallback_card_loader)
    for model, values in report.get("metrics", {}).items():
        if model in recomputed and "characters" in values:
            for code, character in values["characters"].items():
                recomputed[model]["characters"].setdefault(code, {}).update(character)
    return recomputed


def summarize_background(records):
    summary = {}
    for model, rows in records.items():
        characters = {}
        for code in sorted({row["card"] for row in rows}):
            selected = [row for row in rows if row["card"] == code]
            mentions, streak, maximum, missing = 0, 0, 0, 0
            for row in selected:
                reply = row.get("result") or {}
                missing += not reply.get("billable", False)
                content = "\n".join(reply.get("bubbles", []))
                hit = any(word in content for word in ("窗台", "花盆", "贴标签", "贴歪"))
                mentions += hit
                streak = streak + 1 if hit else 0
                maximum = max(maximum, streak)
            characters[code] = dict(
                samples=len(selected),
                missing=missing,
                backgroundMentions=mentions,
                maxConsecutiveMentions=maximum,
                passed=len(selected) == 5 and missing == 0 and maximum < 3,
            )
        summary[model] = dict(characters=characters, passed=all(row["passed"] for row in characters.values()))
    return summary
