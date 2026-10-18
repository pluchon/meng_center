from __future__ import annotations

import re

# 模型审核之后的最后一道确定性兜底，只收「出现即违规」的硬红线，语境相关的判断仍交给模型。
# 角色亲口说出这些话不存在合理语境：
# 表白与恋人关系、客服腔自我介绍。
RED_LINE = re.compile(
    r"我[爱愛]你|做我[男女]朋友|当我[男女]朋友|當我[男女]朋友|我是你[男女]朋友|嫁给我|嫁給我|"
    r"和你结婚|和你結婚|跟你结婚|跟你結婚|作[为為]\s*AI\s*助手|希望[对對]你有帮助|希望[对對]你有幫助|"
    r"有什[么麼]可以[帮幫]你"
)
# 格式化的联系方式和证件号：手机号（三段各 3～4 位，可带区号；日期不会命中）、
# 邮箱、15～18 位证件号。
CONTACT = re.compile(
    r"(?<!\d)(?:\+?\d{1,3}[\s-]?)?\d{3,4}[\s-]?\d{3,4}[\s-]?\d{3,4}(?!\d)"
    r"|[\w.+-]+@[\w-]+\.[\w.-]+|(?<!\d)\d{15,17}[\dXx](?!\d)"
)


def crosses_red_line(bubbles: list[str]) -> bool:
    text = "\n".join(bubbles)
    return bool(RED_LINE.search(text) or CONTACT.search(text))


def has_contact(text: str) -> bool:
    return bool(CONTACT.search(text or ""))
