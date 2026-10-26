"""只保存上下文分段摘要，诊断历史追加和前缀意外变化。"""

import hashlib
import json


def digest(value):
    encoded = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(encoded.encode("utf-8")).hexdigest()


def fingerprints(messages):
    if len(messages) < 3:
        return None
    history = [digest([item.type, item.content]) for item in messages[2:-1]]
    return dict(
        stable=digest([messages[0].type, messages[0].content]),
        slow=digest([messages[1].type, messages[1].content]),
        history=digest(history),
        historyCount=len(history),
        historyHashes=history,
    )


def public_hashes(current):
    return {key: value for key, value in current.items() if key != "historyHashes"}


def changes(previous, current):
    changed = [name for name in ("stable", "slow", "history") if previous[name] != current[name]]
    count = previous["historyCount"]
    appended = (
        current["historyCount"] > count and digest(current["historyHashes"][:count]) == previous["history"]
    )
    return dict(changedSegments=changed, historyAppendOnly=appended)
