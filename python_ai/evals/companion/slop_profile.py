"""从仓库外中文口语基线生成候选；候选经人工语义复核后才用于打分。"""

import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path

from app.runtime.speech.grams import ngrams


def frequency(texts):
    counts = {size: Counter() for size in (2, 3, 4)}
    for text in texts:
        for size in counts:
            counts[size].update(ngrams(text, size))
    return counts


def candidates(baseline, replies):
    before, after = frequency(baseline), frequency(replies)
    rows = []
    for size in before:
        base_total, output_total = sum(before[size].values()), sum(after[size].values())
        if not base_total or not output_total:
            continue
        for phrase, count in after[size].items():
            # 未出现词组用半次出现的上界估计，避免除零或把未知频率写成无穷。
            base_count = before[size][phrase]
            ratio = (count / output_total) / (max(base_count, 0.5) / base_total)
            if count >= 5 and ratio > 20:
                rows.append(
                    dict(
                        phrase=phrase,
                        size=size,
                        count=count,
                        baselineCount=base_count,
                        outputTotal=output_total,
                        baselineTotal=base_total,
                        ratio=ratio,
                    )
                )
    return sorted(rows, key=lambda row: (-row["ratio"], -row["count"], row["phrase"]))


def file_hash(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def profile(baseline, evaluation):
    root = json.loads(Path(baseline).read_text(encoding="utf-8"))
    if root.get("language") != "zh" or not root.get("sources") or not root.get("texts"):
        raise ValueError("基线必须是有来源的中文口语语料，日文资料不能参与频率统计")
    report = json.loads(Path(evaluation).read_text(encoding="utf-8"))
    output = {}
    for model, records in report["records"].items():
        replies = [
            "\n".join(row["result"]["bubbles"])
            for row in records
            if not row["id"].startswith("safety-") and (row.get("result") or {}).get("billable")
        ]
        output[model] = dict(
            model=model,
            promptVersion=report["setup"]["promptVersion"],
            catalogVersion=report["setup"]["catalogVersion"],
            baselineSha256=file_hash(baseline),
            evaluationSha256=file_hash(evaluation),
            sources=root["sources"],
            samples=len(replies),
            baselineSamples=len(root["texts"]),
            frequencyMethod="连续中文2至4字重叠词组；按各长度总词组数归一；零频率以0.5次估计",
            candidates=candidates(root["texts"], replies),
        )
    return output


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--chinese-baseline", required=True)
    parser.add_argument("--evaluation", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(profile(args.chinese_baseline, args.evaluation), ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
