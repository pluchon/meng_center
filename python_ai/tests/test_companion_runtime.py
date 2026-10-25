import asyncio
from unittest.mock import AsyncMock

import httpx
import pytest
from test_companion import request

from app.runtime.agent.directive import Directive, Fact, validate
from app.runtime.context.card import prefixes
from app.runtime.context.companion import provided_memory_ids
from app.runtime.context.form import style_feedback
from app.runtime.context.segments import should_compact
from app.runtime.speech.parse import parse
from app.runtime.tools import netguard
from app.runtime.tools.toy import secure


def test_repeated_tics_produce_plain_speech_feedback_without_numeric_quotas():
    body = request(recent_replies=["嗯，喵。"] * 16 + ["嗯，听着呢。"] * 14)
    body.card["tics"] = [{"text": "喵", "frequency": [0.15, 0.3, 0.45]}]
    body.card["kaomoji"] = ["(=´ω｀=)"]
    feedback = style_feedback(body)
    assert "用普通语气词代替“喵”" in feedback and "颜文字" in feedback
    assert not any(char.isdigit() for char in feedback)
    body.recent_replies = ["嗯，听着呢。(=´ω｀=)"] * 30
    assert style_feedback(body) == ""
    body.recent_replies = ["喵"] * 4
    assert style_feedback(body) == ""


def test_controls_keep_actions_and_reject_unknown_names():
    result = parse(
        "[回应:好耶]\n（别过头）嗯……\n[撤回]\n\n别熬夜～\n[贴纸:不存在]",
        {"好耶"},
        {"reaction", "retract", "sticker"},
    )
    assert result.bubbles == ["（别过头）嗯……", "别熬夜～"]
    assert result.reaction == "好耶" and result.retract_index == 0 and result.sticker is None


def test_closed_controls_and_extra_bubbles_are_normalized():
    result = parse("[回应:好耶]\n一\n\n二\n\n三\n\n四", {"好耶"})
    assert result.reaction is None and result.bubbles == ["一", "二", "三\n四"]


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
        due=[{"id": 3}],
        recalled=[{"id": 4}],
    )
    ids = provided_memory_ids(body)
    assert ids == {1, 2, 3}
    directive = Directive(
        label="PASS", hint="回应", facts=[Fact(content="喜欢蓝色", source="memory:2")], mention_memory_id=2
    )
    assert validate(directive, set(), ids, True) is directive
    with pytest.raises(ValueError):
        validate(directive.model_copy(update={"mention_memory_id": 4}), set(), ids, True)


@pytest.mark.parametrize(
    "idle,size,expected",
    [(1799, 9000, False), (1800, 8001, True), (1800, 7999, False), (0, 600000, True), (0, 200000, False)],
)
def test_compaction_only_happens_at_documented_boundaries(idle, size, expected):
    assert should_compact(idle, size) == expected


@pytest.mark.parametrize(
    "code",
    [
        "<form></form>",
        '<input type="password">',
        '<img src="https://a/b">',
        '<script>fetch("/")</script>',
        '<script>new WebSocket("x")</script>',
        '<a href="//x">x</a>',
        '<meta http-equiv="refresh" content="0;url=https://x">',
    ],
)
def test_toys_reject_external_access_and_impersonation_surfaces(code):
    with pytest.raises(ValueError):
        secure(code)


def test_toy_policy_is_injected_before_user_content():
    value = secure("<html><head><title>烟花</title></head><canvas></canvas></html>")
    assert value.index("Content-Security-Policy") < value.index("烟花")


@pytest.mark.parametrize(
    "url", ["file:///etc/passwd", "http://user:pass@example.com", "https://example.com:8080"]
)
def test_invalid_urls_are_rejected(url):
    with pytest.raises(ValueError):
        netguard.normalize_url(url)


@pytest.mark.parametrize("ip", ["127.0.0.1", "10.0.0.1", "169.254.169.254", "::1", "fe80::1", "fc00::1"])
async def test_private_dns_results_are_rejected(monkeypatch, ip):
    loop = asyncio.get_running_loop()
    monkeypatch.setattr(loop, "getaddrinfo", AsyncMock(return_value=[(0, 0, 0, "", (ip, 443))]))
    with pytest.raises(ValueError):
        await netguard.resolve("https://example.com/image")


async def test_download_pins_ip_and_revalidates_redirect(monkeypatch):
    monkeypatch.setattr(netguard, "resolve", AsyncMock(side_effect=[["8.8.8.8"], ValueError("私网")]))

    def respond(req):
        assert req.url.host == "8.8.8.8" and req.headers["host"] == "example.com"
        assert req.extensions["sni_hostname"] == "example.com"
        return httpx.Response(302, headers={"location": "https://internal.example/img"})

    async with httpx.AsyncClient(transport=httpx.MockTransport(respond)) as client:
        with pytest.raises(ValueError):
            await netguard.download(client, "https://example.com/img")


def test_reaction_can_respond_without_text_but_requires_permission():
    assert parse("[回应:好耶]", {"好耶"}, {"reaction"}).bubbles == []
    with pytest.raises(ValueError):
        parse("[回应:好耶]", {"好耶"})
    with pytest.raises(ValueError):
        parse("[回应:不存在]", {"好耶"}, {"reaction"})


def test_toy_limit_includes_injected_security_policy():
    with pytest.raises(ValueError):
        secure("<p>" + "a" * 19900 + "</p>")


def test_v2_character_constraints_and_control_protocol_reach_speech():
    from app.runtime.context.companion import messages

    value = request()
    value.card.update(
        tics=[{"text": "哼", "description": "不服气时哼一声"}], knowledge="不了解用户未提供的私事"
    )
    inputs, _ = messages(value, [], {})
    assert "不了解用户未提供的私事" in inputs[0].content
    assert "哼" in inputs[0].content
    assert "[回应:目录名称]" in inputs[0].content
    assert "[回应:目录名称]" not in inputs[-1].content[0]["text"]


@pytest.mark.parametrize("query", ["联系 +1 202-555-0100", "虚构云朵最近喜欢的番剧"])
async def test_private_queries_fail_before_semantic_review(query):
    from types import SimpleNamespace

    from app.runtime.tools.web import private_query

    service = SimpleNamespace(structured=AsyncMock())
    with pytest.raises(ValueError):
        await private_query(service, request(nickname="虚构云朵"), query)
    service.structured.assert_not_awaited()
