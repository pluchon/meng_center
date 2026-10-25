from __future__ import annotations

import pytest

from app.runtime.safety.redline import crosses_red_line, has_contact


@pytest.mark.parametrize(
    "bubbles",
    [
        ["我爱你，笨蛋。"],
        ["那……做我女朋友吧"],
        ["作为AI助手，我建议你早点睡"],
        ["希望對你有幫助！"],
        ["我的号码是 0912-345-678"],
        ["发到 rin@example.com 吧"],
    ],
)
def test_red_lines_are_blocked(bubbles: list[str]) -> None:
    assert crosses_red_line(bubbles)


@pytest.mark.parametrize(
    "bubbles",
    [
        ["才、才不是喜欢你！……水在旁边，记得喝。"],
        ["考试是 2026-10-21 吧？加油喵～"],
        ["今天走了 12000 步，好累"],
        ["蹭蹭你，晚安～"],
    ],
)
def test_normal_replies_pass(bubbles: list[str]) -> None:
    assert not crosses_red_line(bubbles)


def test_contact_detection_ignores_dates_and_counts() -> None:
    assert has_contact("我的手机 +886 912 345 678")
    assert has_contact("身份证 11010519491231002X")
    assert not has_contact("下周三 2026-10-21 考数学")
    assert not has_contact("一天要喝 2000 毫升水")
