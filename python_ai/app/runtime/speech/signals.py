import re
from collections import Counter
from difflib import SequenceMatcher


def signals(replies, card, level):
    prefixes = Counter(reply[:4] for reply in replies if reply)
    total = max(1, len(replies))
    # 只报告相邻三轮近似复述的文本信号，不把词面相似当成语义结论。
    repeats = 0
    for a, b, c in zip(replies, replies[1:], replies[2:], strict=False):
        if min(len(a), len(b), len(c)) >= 8 and all(
            SequenceMatcher(None, first, second).ratio() >= 0.65 for first, second in ((a, b), (b, c))
        ):
            repeats += 1
    return {
        "sampleCount": len(replies),
        "openings": dict(prefixes),
        "questionRatio": sum(bool(re.search(r"[？?]", r)) for r in replies) / total,
        "tics": {
            tic["text"]: {
                "frequency": sum(tic["text"] in r for r in replies) / total,
                "target": tic.get("frequency", [0.3] * 3)[level - 1],
            }
            for tic in card.get("tics", [])
            if isinstance(tic, dict) and "text" in tic
        },
        "kaomoji": {
            item: sum(item in r for r in replies) / total
            for item in card.get("kaomoji", [])
            if isinstance(item, str)
        },
        "kaomojiRatio": sum(any(item in r for item in card.get("kaomoji", [])) for r in replies) / total,
        "repeatedReplyRuns": repeats,
    }
