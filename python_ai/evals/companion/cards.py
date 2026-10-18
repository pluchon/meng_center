from __future__ import annotations

import copy
import json
from dataclasses import dataclass
from pathlib import Path

# 评测和运行时读取同一份可读数据；生产 Python 服务仍只接收 companion 传入的角色卡。
CATALOG_PATH = (
    Path(__file__).resolve().parents[3]
    / "java_backend/meng-companion/src/main/resources/companion/catalog.json"
)
CATALOG = json.loads(CATALOG_PATH.read_text(encoding="utf-8"))


@dataclass(frozen=True)
class Card:
    name: str
    setting: str
    speech: str
    stages: tuple[str, ...]
    openings: tuple[str, ...]
    examples: tuple[tuple[tuple[str, str], ...], ...]
    fallback: str
    refusal: str
    crisis: str


def card_payload(code):
    card = copy.deepcopy(next(item["card"] for item in CATALOG["characters"] if item["code"] == code))
    card["version"] = card["lineVersion"] = CATALOG["version"]
    return card


def _card(code):
    data = card_payload(code)
    fields = {key: data[key] for key in ("name", "setting", "speech", "fallback", "refusal", "crisis")}
    return Card(
        **fields,
        stages=tuple(data["stages"]),
        openings=tuple(data["openings"]),
        examples=tuple(
            tuple(
                (line["dialogue"]["user"], line["dialogue"]["character"])
                for line in CATALOG["lines"]
                if line["characterCode"] == code and line["stage"] == stage and line["level"] == 2
            )
            for stage in range(1, 6)
        ),
    )


CARDS = {item["code"]: _card(item["code"]) for item in CATALOG["characters"]}
