import json
from unittest.mock import AsyncMock

import httpx
import pytest
from pydantic import SecretStr

from app.clients.voice import VoiceClient, speakable
from app.config.settings import Settings
from app.runtime.agent.directive import Directive
from app.runtime.models.gateway import ModelGateway


@pytest.mark.parametrize(
    "text,expected",
    [
        ("（耳朵动了动）今天也辛苦啦喵~", "今天也辛苦啦喵~"),
        ("好呀 (=´ω｀=)", "好呀 (chuckle)"),
        ("好呀 ( ˘ω˘ )", "好呀"),
        ("（轻轻笑）你好……呀", "(chuckle)你好<#0.3#>呀"),
        ("唉，算了吧", "(sighs)算了吧"),
        ("嗯……让我想想", "(emm)<#0.3#>让我想想"),
        ("还不睡呀……", "还不睡呀"),
        ("……还不睡呀", "还不睡呀"),
        ("真的……，……吗", "真的<#0.3#>吗"),
        ("(laughs)真的", "真的"),
        ("\ue000真的", "真的"),
        ("哈哈哈，嘿嘿，嘻嘻，够了", "(chuckle)(chuckle)够了"),
        ("（叹气）", ""),
        ("等等<#9.5#>再说", "等等再说"),
        ("（摇尾巴）", ""),
        ("(=・ω・=) ...", ""),
    ],
)
def test_speakable_drops_actions_kaomoji_and_control_syntax(text, expected):
    assert speakable(text) == expected


@pytest.mark.parametrize(
    "text,mood,lead,expected",
    [
        ("真的吗", "HAPPY", True, "(chuckle)真的吗"),
        ("真的吗", "HAPPY", False, "真的吗"),
        ("没事的", "SAD", True, "(sighs)没事的"),
        ("啊？这样", "SURPRISED", True, "(gasps)啊？这样"),
        ("（轻轻笑）真的吗", "SAD", True, "(chuckle)真的吗"),
        ("真的吗", "AUTO", True, "真的吗"),
        ("（轻轻笑）告诉你……一个秘密", "WHISPER", True, "告诉你<#0.3#>一个秘密"),
    ],
)
def test_opening_tone_follows_mood_and_whisper_has_no_tags(text, mood, lead, expected):
    assert speakable(text, mood, lead) == expected


def test_long_bubble_breathes_once_after_first_sentence():
    first = "今天晒了一整天的太阳，舒服得都不想动了。"
    second, third = "你那边天气怎么样呢？", "要是也晴就好啦。"
    assert speakable(first + second + third) == first + "(breath)" + second + third
    assert speakable(first) == first
    long_whisper = speakable(first + second + third, "WHISPER")
    assert "(breath)" not in long_whisper


def client(handler, key="fixture"):
    settings = Settings.model_construct(
        api_key=SecretStr("fixture"), voice_key=SecretStr(key) if key else None
    )
    redis = AsyncMock()
    redis.exists.return_value = False
    redis.eval.return_value = 1
    http = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    return VoiceClient(settings, http, ModelGateway(settings, redis))


async def test_synthesize_sends_cleaned_text_and_returns_base64():
    seen = {}

    def handler(request):
        seen.update(json.loads(request.content))
        return httpx.Response(
            200,
            json={
                "data": {"audio": "4944"},
                "extra_info": {"audio_length": 1200, "usage_characters": 8},
                "base_resp": {"status_code": 0},
            },
        )

    result = await client(handler).synthesize("（歪头）你好呀")
    assert seen["text"] == "你好呀"
    assert seen["voice_setting"]["speed"] == 0.95
    assert "emotion" not in seen["voice_setting"]
    assert seen["model"] == "speech-2.8-hd"
    assert seen["voice_modify"] == {"intensity": 30, "timbre": 20}
    assert seen["voice_setting"]["voice_id"] == "Chinese (Mandarin)_Cute_Spirit"
    assert result == {"audio": "SUQ=", "durationMs": 1200}


async def test_whisper_switches_model_and_keeps_emotion():
    seen = {}

    def handler(request):
        seen.update(json.loads(request.content))
        return httpx.Response(
            200,
            json={
                "data": {"audio": "4944"},
                "extra_info": {"audio_length": 1200},
                "base_resp": {"status_code": 0},
            },
        )

    await client(handler).synthesize("悄悄告诉你", "WHISPER", True)
    assert seen["model"] == "speech-2.6-hd"
    assert seen["voice_setting"]["emotion"] == "whisper"


def test_unknown_agent_mood_falls_back_to_auto():
    assert Directive(label="PASS", voice_mood="SHOUT").voice_mood == "AUTO"
    assert Directive(label="PASS", voice_mood="WHISPER").voice_mood == "WHISPER"


@pytest.mark.parametrize(
    "response",
    [
        httpx.Response(500),
        httpx.Response(200, json={"base_resp": {"status_code": 1004}}),
        httpx.Response(
            200,
            json={"data": {"audio": ""}, "extra_info": {"audio_length": 0}, "base_resp": {"status_code": 0}},
        ),
    ],
)
async def test_failures_fall_back_to_text_only(response):
    assert (await client(lambda request: response).synthesize("你好"))["audio"] is None


async def test_nothing_to_say_or_no_key_makes_no_request():
    def handler(request):
        raise AssertionError("不应该发请求")

    assert (await client(handler).synthesize("（摇尾巴）"))["audio"] is None
    assert (await client(handler, key=None).synthesize("你好"))["audio"] is None
