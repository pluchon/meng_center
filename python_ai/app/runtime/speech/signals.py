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


def _style_signals(replies, card):
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
        "questionRatio": sum(bool(re.search(r"[？?]", r)) for r in replies) / total,
        "tics": {
            tic["text"]: {
                "frequency": sum(tic["text"] in r for r in replies) / total,
                "target": tic.get("frequency", 0.3),
            }
            for tic in card.get("tics", [])
            if isinstance(tic, dict) and "text" in tic
        },
        "kaomojiRatio": sum(any(item in r for item in card.get("kaomoji", [])) for r in replies) / total,
        "repeatedReplyRuns": repeats,
    }


def signals(replies, card):
    return {
        **_style_signals(replies, card),
        "kaomoji": {
            item: sum(item in reply for reply in replies) / max(1, len(replies))
            for item in card.get("kaomoji", []) if isinstance(item, str)
        },
        "openings": dict(Counter(reply[:4] for reply in replies if reply)),
        "selfImitation": imitation_summary(replies),
    }


def review_signals(replies, card):
    """打分只接收当前候选的检查信号；长对话分段统计留在评测报告中。"""
    return {
        **_style_signals(replies, card),
        "openingCount": sum(reply[:4] == replies[-1][:4] for reply in replies if reply) if replies else 0,
        "selfImitation": imitation(replies[-1], replies[:-1]) if replies else None,
    }
