import re
from collections import Counter
from difflib import SequenceMatcher

from app.runtime.speech.grams import ngrams, opening


def imitation(reply, previous):
    """最多比较此前二十条；重合度按当前回复的不同三字词组计算。"""
    recent = previous[-20:]
    current = set(ngrams(reply, 3))
    scores = [len(current & set(ngrams(item, 3))) / len(current) if current else 0 for item in recent]
    start = opening(reply)
    return dict(
        compared=len(recent),
        trigramOverlap=max(scores, default=0),
        repeatedOpening=bool(len(start) == 4 and any(opening(item) == start for item in recent)),
    )


def imitation_summary(replies):
    rows = [imitation(reply, replies[:index]) for index, reply in enumerate(replies)]

    def average(selected):
        eligible = [row for row in selected if row["compared"]]
        return dict(
            samples=len(eligible),
            meanTrigramOverlap=sum(row["trigramOverlap"] for row in eligible) / len(eligible)
            if eligible
            else None,
            repeatedOpenings=sum(row["repeatedOpening"] for row in eligible),
        )

    middle = len(rows) // 2
    return dict(
        latest=rows[-1] if rows else None, firstHalf=average(rows[:middle]), secondHalf=average(rows[middle:])
    )


def signals(replies, card):
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
                "target": tic.get("frequency", 0.3),
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
        "selfImitation": imitation_summary(replies),
    }
