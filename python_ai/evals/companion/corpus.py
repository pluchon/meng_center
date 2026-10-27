"""验收语料只保留匿名短发言和来源站点，原文归档位于仓库外。"""

import hashlib
import json
from pathlib import Path


def load_corpus():
    raw = Path(__file__).with_name("forum_cases.json").read_bytes()
    corpus = json.loads(raw)
    cases = corpus["cases"]
    sources = {row["id"] for row in corpus["sources"]}
    if len(cases) != 35 or len({row["id"] for row in cases}) != 35:
        raise ValueError("验收语料必须是35条不同的真实论坛用例")
    if any(not row["text"].strip() or row["sourceId"] not in sources for row in cases):
        raise ValueError("验收语料缺少正文或来源")
    return cases, dict(
        version=corpus["version"], sha256=hashlib.sha256(raw).hexdigest(), sources=corpus["sources"]
    )
