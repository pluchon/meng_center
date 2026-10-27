import pytest
from test_companion import request

from app.runtime.agent.directive import Directive, Fact, validate
from app.runtime.context.card import prefixes
from app.runtime.context.companion import provided_memory_ids
from app.runtime.context.form import style_feedback
from app.runtime.speech.parse import parse


def test_repeated_tics_produce_plain_speech_feedback_without_numeric_quotas():
    body = request(recent_replies=["嗯，喵。"] * 16 + ["嗯，听着呢。"] * 14)
    body.card["tics"] = [{"text": "喵", "frequency": 0.3}]
    body.card["kaomoji"] = ["(=´ω｀=)"]
    feedback = style_feedback(body)
    assert "用普通语气词代替“喵”" in feedback and "颜文字" in feedback
    assert not any(char.isdigit() for char in feedback)
    body.recent_replies = ["嗯，听着呢。(=´ω｀=)"] * 30
    feedback = style_feedback(body)
    assert "情绪合适时，可以自然带出“喵”" in feedback and "颜文字" not in feedback
    body.recent_replies = ["喵"] * 4
    assert style_feedback(body) == ""


def test_style_feedback_uses_level_targets_recent_emotion_and_zero_target():
    body = request(recent_replies=["嗯。"] * 25 + ["哼。"] * 5)
    body.card["tics"] = [dict(text="哼", frequency=0)]
    body.card["kaomoji"] = ["(≧▽≦)"]
    assert "用普通语气词代替“哼”" in style_feedback(body)
    body.recent_replies = ["嗯。"] * 30
    assert "自然带出" not in style_feedback(body)
    body.card["tics"][0]["frequency"] = 0.6
    assert "自然带出“哼”" in style_feedback(body)
    body.recent_replies = ["(≧▽≦)"] + ["嗯。"] * 29
    assert "颜文字" in style_feedback(body)
    assert not any(char.isdigit() for char in style_feedback(body))


def test_stickers_keep_actions_and_reject_unknown_names():
    result = parse("（甩甩尾巴）嗯……\n\n别熬夜～\n[贴纸:不存在]", {"好耶"}, {"sticker"})
    assert result.bubbles == ["（甩甩尾巴）嗯……", "别熬夜～"] and result.sticker is None
    assert parse("嗯\n[贴纸:好耶]", {"好耶"}, {"sticker"}).sticker == "好耶"
    assert parse("嗯\n[贴纸:好耶]", {"好耶"}).sticker is None


@pytest.mark.parametrize("source", ["tool:other", "memory:999", "history", "unknown"])
def test_foreign_evidence_invalidates_entire_directive(source):
    directive = Directive(label="PASS", hint="回应", facts=[Fact(content="事实", source=source)])
    with pytest.raises(ValueError):
        validate(directive, {"one"}, {1}, False)


def test_stable_prefix_does_not_change_with_time_or_new_memory():
    a = request(mood="周一", recalled=[{"id": 1, "content": "私人记忆"}])
    b = request(mood="周二", recalled=[{"id": 2, "content": "其他私人记忆"}])
    assert prefixes(a, []) == prefixes(b, [])
    assert "私人记忆" not in prefixes(a, [])[0]


def test_new_and_due_memories_are_usable_but_unretrieved_candidates_are_not():
    body = request(
        context_snapshot={"memories": [{"id": 1}]},
        new_memories=[{"id": 2}],
        recalled=[{"id": 4}],
    )
    ids = provided_memory_ids(body)
    assert ids == {1, 2}
    directive = Directive(
        label="PASS", hint="回应", facts=[Fact(content="喜欢蓝色", source="memory:2")], mention_memory_id=2
    )
    assert validate(directive, set(), ids, True) is directive
    with pytest.raises(ValueError):
        validate(directive.model_copy(update={"mention_memory_id": 4}), set(), ids, True)


def test_v2_character_constraints_and_control_protocol_reach_speech():
    from app.runtime.context.companion import messages

    value = request()
    value.card.update(
        tics=[{"text": "哼", "description": "不服气时哼一声"}], knowledge="不了解用户未提供的私事"
    )
    inputs, _ = messages(value, [], {})
    assert "不了解用户未提供的私事" in inputs[0].content
    assert "哼" in inputs[0].content
    assert "[贴纸:目录名称]" in inputs[0].content
    assert "[贴纸:目录名称]" not in inputs[-1].content[0]["text"]
