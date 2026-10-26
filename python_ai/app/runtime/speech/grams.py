"""中文连续词组的确定性计数；标点和消息边界不拼接。"""

import re
import unicodedata
from collections import Counter


def chinese_runs(value):
    return re.findall(r"[\u3400-\u4dbf\u4e00-\u9fff]+", unicodedata.normalize("NFKC", value))


def ngrams(value, size):
    return Counter(
        run[index : index + size] for run in chinese_runs(value) for index in range(len(run) - size + 1)
    )


def opening(value):
    return "".join(chinese_runs(value))[:4]
