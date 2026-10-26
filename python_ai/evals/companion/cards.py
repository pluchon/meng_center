import copy
import json
from pathlib import Path
from types import SimpleNamespace

REPOSITORY = Path(__file__).resolve().parents[3]
CATALOG_PATH = REPOSITORY / "java_backend/meng-companion/src/main/resources/companion/catalog.json"
CATALOG = json.loads(CATALOG_PATH.read_text(encoding="utf-8"))
CARDS = {row["code"]: SimpleNamespace(name=row["card"]["name"]) for row in CATALOG["characters"]}


def configure_catalog(path):
    global CATALOG
    CATALOG = json.loads(Path(path or CATALOG_PATH).read_text(encoding="utf-8"))
    CARDS.clear()
    CARDS.update({row["code"]: SimpleNamespace(name=row["card"]["name"]) for row in CATALOG["characters"]})


def card_payload(code):
    row = next(row for row in CATALOG["characters"] if row["code"] == code)
    return copy.deepcopy({**row["card"], "version": CATALOG["version"]})


def examples_payload(code, stage=3, level=2):
    return [
        {**row, "id": index + 1}
        for index, row in enumerate(CATALOG["lines"])
        if row["characterCode"] == code and row["stage"] == stage and row["level"] == level
    ]


def emotion_payload():
    path = REPOSITORY / "java_backend/meng-content/src/main/resources/emotion/catalog.json"
    root = json.loads(path.read_text(encoding="utf-8"))
    return [row for row in root["emotions"] if row["enabled"] and row["series"] == "cirno"]
