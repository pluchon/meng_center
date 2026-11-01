import asyncio
import base64
import json
from types import SimpleNamespace
from unittest.mock import AsyncMock

import httpx
import pytest
from pydantic import SecretStr

from app.clients.voice import MOODS, VoiceClient, speakable, ssml, vetted
from app.config.settings import Settings
from app.runtime.agent.directive import Directive
from app.runtime.models.gateway import ModelGateway
from app.schemas.companion import VoiceRequest
from app.services.companion import Companion


@pytest.mark.parametrize(
    "text,expected",
    [
        ("（耳朵动了动）今天也辛苦啦喵~", "今天也辛苦啦喵~"),
        ("好呀 (=´ω｀=)", "好呀 [giggles]"),
        ("好呀 ( ˘ω˘ )", "好呀"),
        ("（轻轻笑）你好……呀", "[giggles]你好<break time=\"300ms\"/>呀"),
        ("唉，算了吧", "[sighing]算了吧"),
        ("嗯……让我想想", "嗯<break time=\"300ms\"/>让我想想"),
        ("还不睡呀……", "还不睡呀"),
        ("……还不睡呀", "还不睡呀"),
        ("真的……，……吗", "真的<break time=\"300ms\"/>吗"),
        ("[laughing]真的", "真的"),
        ("(laughs)真的", "真的"),
        ("\ue000真的", "真的"),
        ("哈哈哈，嘿嘿，嘻嘻，够了", "[laughing][giggles]够了"),
        ("（清清嗓子）本喵宣布", "[clears throat]本喵宣布"),
        ("咳咳，本喵宣布", "[cough]本喵宣布"),
        ("（哼）才不是", "[snorts]才不是"),
        ("（哼着歌）今天真好", "今天真好"),
        ("（大笑）真的", "[laughing]真的"),
        ("[excited]真的", "真的"),
        ("（叹气）", ""),
        ("（摇尾巴）", ""),
        ("(=・ω・=) ...", ""),
    ],
)
def test_speakable_drops_actions_kaomoji_and_control_syntax(text, expected):
    assert speakable(text) == expected


@pytest.mark.parametrize(
    "text,mood,lead,expected",
    [
        ("真的吗", "HAPPY", True, "[excited][giggles]真的吗"),
        ("真的吗", "HAPPY", False, "[excited]真的吗"),
        ("没事的", "SAD", True, "[empathetic][sighing]没事的"),
        ("啊？这样", "SURPRISED", True, "[curious][gasp]啊？这样"),
        ("（轻轻笑）真的吗", "SAD", True, "[empathetic][giggles]真的吗"),
        ("真的吗", "AUTO", True, "真的吗"),
        ("悄悄告诉你", "WHISPER", True, "[whispers]悄悄告诉你"),
        ("这样可以", "CALM", True, "[serious]这样可以"),
    ],
)
def test_opening_tone_follows_mood(text, mood, lead, expected):
    assert speakable(text, mood, lead) == expected


# 24kHz、160kbps 的 MP3 帧头，每秒 20000 字节
MP3 = bytes.fromhex("fff3e4c4") + bytes(19996)
ID3 = b"ID3\x03\x00\x00\x00\x00\x00\x51" + bytes(81)


def client(handler):
    settings = Settings.model_construct(api_key=SecretStr("fixture"))
    redis = AsyncMock()
    redis.exists.return_value = False
    redis.eval.return_value = 1
    http = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    return VoiceClient(settings, http, ModelGateway(settings, redis))


def bailian(seen, audio=MP3):
    def handler(request):
        if request.method == "GET":
            assert "Authorization" not in request.headers
            return httpx.Response(200, content=audio)
        seen.update(json.loads(request.content))
        seen["authorization"] = request.headers["Authorization"]
        return httpx.Response(
            200,
            json={
                "output": {"audio": {"url": "https://oss.example/a.mp3", "data": ""}},
                "usage": {"input_tokens": 8, "output_tokens": 20},
            },
        )

    return handler


async def test_synthesize_sends_cleaned_text_and_returns_base64():
    seen = {}
    result = await client(bailian(seen)).synthesize("你好呀")
    assert seen["model"] == "qwen-audio-3.1-tts-flash"
    assert seen["authorization"] == "Bearer fixture"
    assert seen["input"]["text"] == "<speak>你好呀</speak>"
    assert seen["input"]["enable_ssml"] is True
    assert seen["input"]["voice"] == "longling_v3.1"
    assert seen["input"]["format"] == "mp3"
    assert seen["input"]["language_hints"] == ["zh"]
    assert {key: seen["input"][key] for key in MOODS["AUTO"]} == MOODS["AUTO"]
    assert result == {"audio": base64.b64encode(MP3).decode(), "durationMs": 1000}


async def test_mood_changes_voice_settings_but_not_model():
    seen = {}
    await client(bailian(seen)).synthesize("悄悄告诉你", "WHISPER")
    assert seen["model"] == "qwen-audio-3.1-tts-flash"
    assert {key: seen["input"][key] for key in MOODS["WHISPER"]} == MOODS["WHISPER"]


def test_ssml_keeps_breaks_and_escapes_everything_else():
    assert ssml('A&B<break time="300ms"/>"好"\'呀\'<b>') == (
        '<speak>A&amp;B<break time="300ms"/>&quot;好&quot;&apos;呀&apos;&lt;b&gt;</speak>'
    )


async def test_duration_skips_id3_tag():
    result = await client(bailian({}, ID3 + MP3)).synthesize("你好")
    assert result["durationMs"] == 1000


def test_unknown_agent_mood_falls_back_to_auto():
    assert Directive(label="PASS", voice_mood="SHOUT").voice_mood == "AUTO"
    assert Directive(label="PASS", voice_mood="WHISPER").voice_mood == "WHISPER"


@pytest.mark.parametrize(
    "response",
    [
        httpx.Response(500),
        httpx.Response(400, json={"code": "InvalidParameter", "message": "Engine error"}),
        httpx.Response(200, json={"output": {"audio": {"url": ""}}}),
        httpx.Response(200, json={"code": "Throttling"}),
    ],
)
async def test_failures_fall_back_to_text_only(response):
    assert (await client(lambda request: response).synthesize("你好"))["audio"] is None


@pytest.mark.parametrize("audio", [b"", b"RIFF0000WAVEfmt ", bytes.fromhex("fffbe4c4")])
async def test_unexpected_audio_falls_back_to_text_only(audio):
    assert (await client(bailian({}, audio)).synthesize("你好"))["audio"] is None


BASE = "满分！好厉害啊你，今天考了100分"


@pytest.mark.parametrize(
    "script,accepted",
    [
        ("哇，满分？！[giggles]也太厉害了吧你，考了100分呢", True),
        ("[gasp]满分！好厉害啊你，100分", True),
        ("[clears throat]满分！[very fast]好厉害啊你，100分", True),
        ("[like dracula]满分！好厉害啊你，100分", False),
        ("唔，满分呀，也太厉害了，今天考了100分", True),
        ("满分！好厉害，今天考了99分", False),
        ("满分！好厉害", False),
        ("满分！今天考了100分" + "特别" * 30, False),
        ("满分！好厉害，100分[screams]", False),
        ("满分！好厉害，100分(chuckle)", False),
        ('满分！<break time="300ms"/>好厉害，今天考了100分', True),
        ('满分！<break time="900ms"/>好厉害，今天考了100分', False),
        ('满分！<break time="3s"/>好厉害，今天考了100分', False),
        ('<break time="200ms"/>满分！好厉害，今天考了100分', False),
        ('满分！好厉害，今天考了100分<break time="200ms"/>', False),
        ('满<break time="200ms"/><break time="300ms"/>分！好厉害，今天考了100分', False),
        ("满分！<#0.3#>好厉害，今天考了100分", False),
        ('满分！<speak rate="2">好厉害</speak>，今天考了100分', False),
        ("[excited][giggles][sighing][gasp][snorts]满分！好厉害，今天考了100分", False),
        ("", False),
    ],
)
def test_script_structure_is_checked_in_code(script, accepted):
    assert (vetted(script, BASE) is not None) is accepted


@pytest.mark.parametrize(
    "script,expected",
    [
        ("满分！ [gasp] 好厉害，今天考了100分", "满分！[gasp]好厉害，今天考了100分"),
        ('满分<break time="200ms"/>呀！好厉害，今天考了100分', "满分呀！好厉害，今天考了100分"),
        (
            '满<break time="100ms"/>分<break time="200ms"/>！好<break time="300ms"/>厉害，今天考了100分',
            '满<break time="100ms"/>分<break time="200ms"/>！好厉害，今天考了100分',
        ),
    ],
)
def test_script_marks_are_tidied(script, expected):
    assert vetted(script, BASE) == expected


def service(content=None, error=None, review=True, review_error=None):
    voice = SimpleNamespace(
        synthesize=AsyncMock(
            side_effect=lambda text, mood: {"audio": "voice:" + text, "durationMs": 1}
        )
    )
    answer = SimpleNamespace(content=content)
    models = SimpleNamespace(
        settings=SimpleNamespace(voice_script_seconds=1),
        semaphores={"assist": asyncio.Semaphore(1), "judge": asyncio.Semaphore(1)},
        assist=SimpleNamespace(ainvoke=AsyncMock(return_value=answer, side_effect=error)),
        voice=voice,
    )
    chain = SimpleNamespace(
        ainvoke=AsyncMock(return_value=SimpleNamespace(safe=review), side_effect=review_error)
    )
    fake = Companion.__new__(Companion)
    fake.models = models
    fake.tasks = SimpleNamespace(output=lambda name, attempts=None: chain)
    return fake, voice, chain


async def test_reviewed_script_is_spoken():
    fake, voice, chain = service("哇，你好你好")
    result = await Companion.voice(fake, VoiceRequest(text="你好呀", mood="HAPPY"))
    assert result["audio"] == "voice:哇，你好你好"
    voice.synthesize.assert_awaited_once_with("哇，你好你好", "HAPPY")
    chain.ainvoke.assert_awaited_once()


@pytest.mark.parametrize("review,review_error", [(False, None), (True, TimeoutError())])
async def test_unreviewed_script_is_never_returned(review, review_error):
    fake, voice, _ = service("哇，你好你好", review=review, review_error=review_error)
    result = await Companion.voice(fake, VoiceRequest(text="你好呀", mood="HAPPY"))
    assert result["audio"] == "voice:[excited]你好呀"


@pytest.mark.parametrize(
    "content,error",
    [
        ("[screams]你好", None),
        ("我爱你，你好呀", None),
        (None, TimeoutError()),
    ],
)
async def test_bad_or_failed_script_falls_back_without_review(content, error):
    fake, voice, chain = service(content, error)
    await Companion.voice(fake, VoiceRequest(text="（轻轻笑）你好呀", mood="AUTO"))
    voice.synthesize.assert_awaited_once_with("[giggles]你好呀", "AUTO")
    chain.ainvoke.assert_not_awaited()


async def test_nothing_speakable_skips_the_model():
    fake, voice, _ = service("喵")
    assert (await Companion.voice(fake, VoiceRequest(text="（摇尾巴）")))["audio"] is None
    fake.models.assist.ainvoke.assert_not_awaited()
    voice.synthesize.assert_not_awaited()


async def test_nothing_to_say_makes_no_request():
    def handler(request):
        raise AssertionError("不应该发请求")

    assert (await client(handler).synthesize(""))["audio"] is None
