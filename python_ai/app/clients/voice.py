from __future__ import annotations

import asyncio
import base64
import logging
import re
from xml.sax.saxutils import escape

LOG = logging.getLogger(__name__)
# 先把语气换成私用区占位符再清洗，回复文字里自带的 [laughing]、(笑) 不会被当成控制语法
GIGGLE, SIGH, GASP, LAUGH, SNORT, COUGH, CLEAR = "\ue000\ue001\ue002\ue003\ue004\ue005\ue006"
PAUSE = "\ue007"
TONES = {
    GIGGLE: "[giggles]",
    SIGH: "[sighing]",
    GASP: "[gasp]",
    LAUGH: "[laughing]",
    SNORT: "[snorts]",
    COUGH: "[cough]",
    CLEAR: "[clears throat]",
}
BRACKETED = re.compile(r"[（(][^（）()]*[）)]|\[[^\[\]]*\]")
# 括号里的动作和笑意颜文字转成语气，其余括号内容只显示不念
BRACKET_TONES = (
    (re.compile(r"清嗓"), CLEAR),
    (re.compile(r"咳"), COUGH),
    (re.compile(r"大笑"), LAUGH),
    (re.compile(r"笑|´ω|￣ω"), GIGGLE),
    (re.compile(r"哼(?!歌|着)"), SNORT),
    (re.compile(r"叹|懒腰|哈欠|呼气|松了口气"), SIGH),
    (re.compile(r"惊|吓|愣|倒吸"), GASP),
)
WORD_TONES = (
    (re.compile(r"哈{3,}"), LAUGH),
    (re.compile(r"哈哈|嘿嘿+|嘻嘻+"), GIGGLE),
    (re.compile(r"咳咳+"), COUGH),
    (re.compile(r"唉+"), SIGH),
    (re.compile(r"…+|\.{3,}"), PAUSE),
)
# 只留文字、常用标点和占位符
UNSPOKEN = re.compile(r"[^\w\s，。！？、；：,.!?;:~～\ue000-\ue007]|_")
PLACEHOLDER = re.compile(r"[\ue000-\ue007]")
TONE_CHARS = re.compile(r"[\ue000-\ue006]")
AFTER_TONE = re.compile(r"([\ue000-\ue006])[，,、\s]+")
PAUSES = re.compile(r"\ue007(?:[\s，,、。]*\ue007)*")
MAX_TONES = 2
SILENT = {"audio": None, "durationMs": 0}
# 官方标签里只去掉吸血鬼、低吼和轻蔑，它们不是朋友之间说话的语气
SCRIPT_TAGS = re.compile(
    r"\[(?:giggles|laughing|sighing|gasp|snorts|cough|clears throat"
    r"|excited|amazed|curious|mischievously|empathetic|sad|tired|bored|reluctantly|serious"
    r"|panicked|trembling|crying|whispers|asmr|sarcastic|angry|shouting|very slowly|very fast)\]"
)
BREAK = '<break time="300ms"/>'
ANY_PAUSE = re.compile(r'<break time="[1-5]00ms"/>')
MARKS = re.compile(SCRIPT_TAGS.pattern + "|" + ANY_PAUSE.pattern)
BAD_PAUSE = re.compile(r"^\s*<break|/>\s*$|/>\s*<break")
# 句尾助词要贴着前面的字念，前面不能停顿
PAUSE_BEFORE_PARTICLE = re.compile(ANY_PAUSE.pattern + r"(?=[呐嘛呀啦哦呢吧喵])")
SPACED_MARK = re.compile(r"\s*(" + MARKS.pattern + r")\s*")
LITERALS = re.compile(r"[0-9A-Za-z]+")
MAX_SCRIPT_TONES = 4
MAX_SCRIPT_PAUSES = 2
# 这个音色默认约每秒 3.7 字，instruction 里写轻快也提不起来，语速要靠 rate
MOODS = {
    "AUTO": {"instruction": "用撒娇活泼的语气说", "rate": 1.15, "pitch": 1.03, "volume": 50},
    "HAPPY": {"instruction": "用开心活泼、有点小得意的语气说", "rate": 1.2, "pitch": 1.06, "volume": 55},
    "SAD": {"instruction": "用温柔轻缓的语气，轻轻地安慰对方", "rate": 1.1, "pitch": 0.97, "volume": 45},
    "SURPRISED": {"instruction": "用又惊讶又好奇的语气说", "rate": 1.2, "pitch": 1.05, "volume": 55},
    "CALM": {"instruction": "用平实清楚的语气，认真地说", "rate": 1.1, "pitch": 1.0, "volume": 50},
    "WHISPER": {"instruction": "贴着耳朵，小声地说悄悄话", "rate": 1.0, "pitch": 1.0, "volume": 40},
}
# 控制类标签只管到自动切句处，所以 instruction 负责整段，标签负责开头这一句
MOOD_TAGS = {
    "HAPPY": "[excited]",
    "SAD": "[empathetic]",
    "SURPRISED": "[curious]",
    "CALM": "[serious]",
    "WHISPER": "[whispers]",
}
OPENING = {"HAPPY": GIGGLE, "SAD": SIGH, "SURPRISED": GASP}
SAMPLE_RATE = 24000
# MPEG-2 Layer III 的码率表，单位 kbps；接口不返回时长，只能从音频头算
BITRATES = (0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)


def duration_ms(audio: bytes) -> int:
    start = 0
    if audio[:3] == b"ID3":
        start = 10 + (audio[6] << 21 | audio[7] << 14 | audio[8] << 7 | audio[9])
    header = int.from_bytes(audio[start : start + 4], "big")
    # 同步位、MPEG-2、Layer III，其他格式算不准时长，当作合成失败
    if header >> 17 != 0x7FF9:
        raise ValueError("语音合成结果不是预期的格式")
    return (len(audio) - start) * 8 // BITRATES[header >> 12 & 15]


def bracket_tone(match: re.Match) -> str:
    return next((tone for pattern, tone in BRACKET_TONES if pattern.search(match.group())), "")


def speakable(text: str, mood: str = "AUTO", lead: bool = False) -> str:
    spoken = BRACKETED.sub(bracket_tone, PLACEHOLDER.sub("", text))
    for pattern, tone in WORD_TONES:
        spoken = pattern.sub(tone, spoken)
    spoken = AFTER_TONE.sub(r"\1", UNSPOKEN.sub("", spoken))
    spoken = PAUSES.sub(PAUSE, spoken).strip(PAUSE + " \n，,、")
    if not re.search(r"[^\W_]", PLACEHOLDER.sub("", spoken)):
        return ""
    # 回复里没有现成的语气时，一轮的开头按情绪补一个
    if lead and mood in OPENING and not TONE_CHARS.search(spoken):
        spoken = OPENING[mood] + spoken
    # 一个气泡里语气词多了反而假，只留前两个；停顿只能放在两段可发音的内容之间
    kept = 0
    parts = []
    for char in spoken:
        if char in TONES:
            kept += 1
            if kept > MAX_TONES:
                continue
            parts.append(TONES[char])
        else:
            parts.append(BREAK if char == PAUSE else char)
    return MOOD_TAGS.get(mood, "") + "".join(parts).strip()


def unmarked(text: str) -> str:
    return re.sub(r"[^\w]|_", "", MARKS.sub("", text))


# 口语稿可以改说法，这里只查代码查得出的：标记合法、长度相近、数字和字母原样；有没有改意思交给复核
def vetted(script: str, base: str) -> str | None:
    script = PAUSE_BEFORE_PARTICLE.sub("", SPACED_MARK.sub(r"\1", script.strip()))
    for pause in ANY_PAUSE.findall(script)[MAX_SCRIPT_PAUSES:]:
        head, _, tail = script.rpartition(pause)
        script = head + tail
    words, original = unmarked(script), unmarked(base)
    if (
        not words
        or len(SCRIPT_TAGS.findall(script)) > MAX_SCRIPT_TONES
        or BAD_PAUSE.search(script)
        or re.search(r"[\[\]()（）<>#]", MARKS.sub("", script))
        or not len(original) / 2 <= len(words) <= len(original) * 3 / 2 + 10
        or sorted(LITERALS.findall(words)) != sorted(LITERALS.findall(original))
    ):
        return None
    return script


# 只有代码放进去的停顿是 SSML，其余文字一律转义
def ssml(spoken: str) -> str:
    entities = {'"': "&quot;", "'": "&apos;"}
    parts = re.split("(" + ANY_PAUSE.pattern + ")", spoken)
    return "<speak>" + "".join(
        part if ANY_PAUSE.fullmatch(part) else escape(part, entities) for part in parts
    ) + "</speak>"


class VoiceClient:
    def __init__(self, settings, http, gateway):
        self.settings, self.http, self.gateway = settings, http, gateway

    # 语音只是文字的附加：合成失败或没有可念的内容都返回无语音，不影响这一轮
    async def synthesize(self, spoken: str, mood: str = "AUTO") -> dict:
        if not spoken:
            return SILENT
        model = self.settings.voice_model

        async def call():
            async with asyncio.timeout(self.settings.voice_timeout):
                response = await self.http.post(
                    self.settings.voice_url,
                    headers=self.gateway.headers(),
                    timeout=self.settings.voice_timeout,
                    json={
                        "model": model,
                        "input": {
                            "text": ssml(spoken),
                            "enable_ssml": True,
                            "voice": self.settings.voice_id,
                            "format": "mp3",
                            "sample_rate": SAMPLE_RATE,
                            "language_hints": ["zh"],
                            **MOODS[mood],
                        },
                    },
                )
                response.raise_for_status()
                result = response.json()
                # 非流式合成只返回下载地址，24 小时有效，所以要立刻取回来
                audio = await self.http.get(
                    result["output"]["audio"]["url"], timeout=self.settings.voice_timeout
                )
                audio.raise_for_status()
                return result, audio.content

        try:
            _, audio = await self.gateway.invoke(
                model, "voice", len(spoken), call, lambda r: r[0].get("usage", {}), retry=False
            )
            if not audio:
                raise ValueError("语音合成结果为空")
            duration = duration_ms(audio)
        except Exception as error:
            LOG.warning("voice_failed error_type=%s", type(error).__name__)
            return SILENT
        return {"audio": base64.b64encode(audio).decode(), "durationMs": duration}
