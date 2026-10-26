"""对比结论必须能核对外部基线、单一变量和其余生成条件。"""

import copy
import hashlib
import json
from dataclasses import asdict
from pathlib import Path

from app.runtime.context.cache import digest
from app.runtime.models.registry import registry
from evals.companion import cards


def generation_conditions(settings):
    root = Path(__file__).resolve().parents[2]
    files = sorted((root / "app").rglob("*.py")) + [
        Path(__file__).with_name("cases.py"),
        Path(__file__).with_name("cards.py"),
    ]
    code = {str(path.relative_to(root)): hashlib.sha256(path.read_bytes()).hexdigest() for path in files}
    catalog = copy.deepcopy(cards.CATALOG)
    catalog.pop("version", None)
    for item in catalog["characters"]:
        item["ticTargets"] = [row.get("frequency", []) for row in item["card"].get("tics", [])]
        for field in ("voice", "tics", "kaomoji"):
            item["card"].pop(field, None)
    for row in catalog["lines"]:
        row["dialogue"].pop("character", None)
    return dict(
        runtime=digest(code),
        fixedCatalog=digest(catalog),
        slopProfiles={
            path.name: hashlib.sha256(path.read_bytes()).hexdigest()
            for path in sorted(Path(__file__).with_name("slop").glob("*.json"))
        },
        # 检查点经 JSON 写入后元组会变成列表；生成条件从一开始就使用相同类型。
        models={name: json.loads(json.dumps(asdict(spec))) for name, spec in registry(settings).items()},
        baseUrl=settings.base_url,
        structuredMethod=settings.structured_method,
        speculative=settings.speculative,
    )


def comparison_plan(baseline, variables, output):
    if bool(baseline) != bool(variables):
        raise ValueError("生成对比结论必须同时注明基线路径和唯一改动变量")
    if len(variables) > 1 or (variables and not variables[0].strip()):
        raise ValueError("一次对比只能声明一个非空变量")
    if not baseline:
        return None, dict(
            baseline=None, changedVariables=[], canConclude=False, reason="未指定基线，仅报告本批结果"
        )
    path = Path(baseline).resolve()
    repository = cards.REPOSITORY.resolve()
    if path.is_relative_to(repository) or path == Path(output).resolve():
        raise ValueError("基线必须保存在仓库外，且不能覆盖为本次报告")
    raw = path.read_bytes()
    previous = json.loads(raw)
    if not previous.get("complete"):
        raise ValueError("对比基线必须是完整报告")
    return previous, dict(
        baseline=str(path),
        baselineSha256=hashlib.sha256(raw).hexdigest(),
        changedVariables=variables,
        canConclude=False,
    )


def comparison_status(plan, current_setup, previous):
    if previous is None:
        return plan
    prior = previous.get("setup", {})
    checked = (
        "promptVersion",
        "candidates",
        "character",
        "smallSample",
        "smoke",
        "speculative",
        "speechProvider",
        "generationConditions",
        "generationProtocolVersion",
        "blindProtocolVersion",
        "blindJudges",
        "backgroundProtocolVersion",
        "providerFailureProtocolVersion",
        "scene",
        "seed",
    )
    differences = [name for name in checked if prior.get(name) != current_setup.get(name)]
    if "generationConditions" not in prior:
        differences.append("baselineGenerationConditionsMissing")
    allowed = plan["changedVariables"] == ["角色卡说话方式"]
    return {
        **plan,
        "canConclude": allowed and not differences,
        "uncontrolledChanges": sorted(set(differences)),
        "reason": "仅角色卡说话方式变化"
        if allowed and not differences
        else "生成条件未保持一致或缺少可核对证据，只能作描述性对照",
    }
