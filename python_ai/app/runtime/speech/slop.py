"""只读取经过逐条复核且与生成版本一致的套话表。"""

import json
import re
from functools import lru_cache
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3] / "evals/companion/slop"


def load(model):
    if not re.fullmatch(r"[a-zA-Z0-9._-]+", model):
        return {}
    path = ROOT / (model + ".json")
    return read_version(str(path), path.stat().st_mtime_ns) if path.is_file() else {}


@lru_cache(maxsize=16)
def read_version(path, modified):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def signals(model, reply, prompt_version, catalog_version):
    profile = load(model)
    applicable = (
        bool(profile)
        and profile.get("promptVersion") == prompt_version
        and str(profile.get("catalogVersion")) == str(catalog_version)
    )
    matches = [
        dict(phrase=row["phrase"], count=reply.count(row["phrase"]))
        for row in profile.get("reviewed", [])
        if applicable and row.get("isSlop") is True and row.get("reason") and row["phrase"] in reply
    ]
    return dict(profileApplicable=applicable, matches=matches)
