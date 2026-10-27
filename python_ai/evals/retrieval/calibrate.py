"""只分析既有原始重排分数；不导入模型客户端、不访问网络、不修改配置。"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path

DATASET = Path(__file__).with_name("cases.jsonl")
SCENES = ("search", "related", "recall")


def dataset(path=DATASET):
    raw = path.read_bytes()
    rows = [json.loads(line) for line in raw.decode("utf-8").splitlines() if line.strip()]
    ids, queries = set(), set()
    for row in rows:
        identifier = row["id"]
        query = (row["scene"], row["query"])
        documents, relevant = row["documents"], row["relevant"]
        if (
            identifier in ids
            or query in queries
            or row["scene"] not in SCENES
            or row["split"] not in {"calibration", "validation"}
            or not isinstance(identifier, str)
            or not row["query"].strip()
            or not 2 <= len(documents) <= 20
            or any(not isinstance(item, str) or not item.strip() for item in documents)
            or len(set(relevant)) != len(relevant)
            or any(type(index) is not int or not 0 <= index < len(documents) for index in relevant)
        ):
            raise ValueError("标注集包含重复、泄漏或无效用例")
        ids.add(identifier)
        queries.add(query)
    for scene in SCENES:
        for split, minimum in (("calibration", 8), ("validation", 4)):
            part = [row for row in rows if row["scene"] == scene and row["split"] == split]
            if (
                len(part) < minimum
                or not any(row["relevant"] for row in part)
                or not any(len(row["relevant"]) < len(row["documents"]) for row in part)
            ):
                raise ValueError("各场景需要独立的校准与验证样本，且均含正负标注")
    canonical = json.dumps(rows, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return rows, hashlib.sha256(canonical).hexdigest()


def metrics(rows, scores, threshold):
    tp = fp = fn = 0
    for row in rows:
        expected = set(row["relevant"])
        selected = {index for index, score in enumerate(scores[row["id"]]) if score >= threshold}
        tp += len(selected & expected)
        fp += len(selected - expected)
        fn += len(expected - selected)
    return {
        "precision": tp / (tp + fp) if tp + fp else 0,
        "recall": tp / (tp + fn) if tp + fn else 0,
        "f1": 2 * tp / (2 * tp + fp + fn) if tp + fp + fn else 0,
        "truePositives": tp,
        "falsePositives": fp,
        "falseNegatives": fn,
        "queries": len(rows),
    }


def calibrate(archive, defaults, expected_model, path=DATASET):
    rows, digest = dataset(path)
    if archive.get("datasetSha256") != digest or archive.get("model") != expected_model:
        raise ValueError("原始分数的标注集或模型与当前条件不一致")
    if archive.get("format") != "raw-rerank-v1" or set(defaults) != set(SCENES):
        raise ValueError("缺少原始分数协议或场景阈值")
    instructions = archive.get("instructions", {})
    if set(instructions) != set(SCENES) or any(
        not isinstance(value, str) or not value.strip() for value in instructions.values()
    ):
        raise ValueError("缺少原始调用的场景指令")
    scores = {}
    by_id = {row["id"]: row for row in rows}
    for record in archive["records"]:
        identifier, values = record["id"], record["scores"]
        if (
            identifier not in by_id
            or identifier in scores
            or record.get("reranked") is not True
            or len(values) != len(by_id[identifier]["documents"])
            or any(
                type(value) not in {int, float} or not math.isfinite(value) or not 0 <= value <= 1
                for value in values
            )
        ):
            raise ValueError("分数缺失、重复、使用降级顺序或不在有效范围")
        scores[identifier] = values
    if set(scores) != set(by_id):
        raise ValueError("原始分数不完整，不能形成阈值结论")
    result = {}
    for scene in SCENES:
        default = defaults[scene]
        if type(default) not in {int, float} or not math.isfinite(default) or not 0 <= default <= 1:
            raise ValueError("默认阈值不合法")
        training = [row for row in rows if row["scene"] == scene and row["split"] == "calibration"]
        held_out = [row for row in rows if row["scene"] == scene and row["split"] == "validation"]
        candidates = [(value / 100, metrics(training, scores, value / 100)) for value in range(101)]
        threshold, trained = max(
            candidates,
            key=lambda item: (item[1]["f1"], item[1]["precision"], -abs(item[0] - default), item[0]),
        )
        baseline = metrics(held_out, scores, default)
        validation = metrics(held_out, scores, threshold)
        accepted = validation["f1"] >= baseline["f1"] and validation["recall"] >= baseline["recall"]
        result[scene] = {
            "default": default,
            "candidate": threshold,
            "recommended": threshold if accepted else None,
            "status": "VALIDATED" if accepted else "VALIDATION_REGRESSION",
            "calibration": trained,
            "validation": validation,
            "defaultValidation": baseline,
        }
    return {
        "datasetSha256": digest,
        "model": expected_model,
        "instructions": instructions,
        "scenes": result,
        "configurationChanged": False,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scores", type=Path, required=True)
    parser.add_argument("--model", required=True)
    parser.add_argument("--config", type=Path, default=Path("../deploy/nacos/meng-ai.yml"))
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    archive = json.loads(args.scores.read_text(encoding="utf-8"))
    report = calibrate(
        archive.get("retrieval", archive),
        json.loads(args.config.read_text(encoding="utf-8"))["rerank_thresholds"],
        args.model,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8") as file:
        json.dump(report, file, ensure_ascii=False, indent=2, allow_nan=False)


if __name__ == "__main__":
    main()
