from __future__ import annotations

import base64
import logging
import re

LOG = logging.getLogger(__name__)
# 先把语气换成私用区占位符再清洗，回复文字里自带的 (laughs)、<#x#> 不会被当成控制语法
LAUGH, SIGH, EXHALE, HUM, INHALE, EMM, GASP, BREATH = "\ue000\ue001\ue002\ue003\ue004\ue005\ue006\ue007"
PAUSE = "\ue008"
TONES = {
    LAUGH: "(chuckle)",
    SIGH: "(sighs)",
    EXHALE: "(exhale)",
    HUM: "(humming)",
    INHALE: "(inhale)",
    EMM: "(emm)",
    GASP: "(gasps)",
    BREATH: "(breath)",
}
BRACKETED = re.compile(r"[（(][^（）()]*[）)]|<#[^#]*#>")
# 括号里的动作和笑意颜文字转成语气，其余括号内容只显示不念
BRACKET_TONES = (
    (re.compile(r"笑|´ω|￣ω"), LAUGH),
    (re.compile(r"叹"), SIGH),
    (re.compile(r"懒腰|哈欠|呼气|松了口气"), EXHALE),
    (re.compile(r"哼歌|哼着"), HUM),
    (re.compile(r"吸气|深呼吸"), INHALE),
    (re.compile(r"惊|吓|愣"), GASP),
)
WORD_TONES = (
    (re.compile(r"哈哈+|嘿嘿+|嘻嘻+"), LAUGH),
    (re.compile(r"唉+"), SIGH),
    (re.compile(r"嗯+(?=[…。，,.~～]|$)"), EMM),
    (re.compile(r"…+|\.{3,}"), PAUSE),
)
# 只留文字、常用标点和占位符
UNSPOKEN = re.compile(r"[^\w\s，。！？、；：,.!?;:~～\ue000-\ue008]|_")
PLACEHOLDER = re.compile(r"[\ue000-\ue008]")
TONE_CHARS = re.compile(r"[\ue000-\ue007]")
AFTER_TONE = re.compile(r"([\ue000-\ue007])[，,、\s]+")
PAUSES = re.compile(r"\ue008(?:[\s，,、。]*\ue008)*")
# 第一句说完换一口气；太短的气泡不换，后面没有话也不换
FIRST_SENTENCE = re.compile(r"[。！？!?]+(?=[^\s\ue000-\ue008])")
BREATH_FROM = 30
MAX_TONES = 2
SILENT = {"audio": None, "durationMs": 0}
SCRIPT_TAGS = re.compile(r"\((?:chuckle|laughs|sighs|gasps|breath|humming|emm|inhale|exhale|sniffs)\)")
MARKS = re.compile(SCRIPT_TAGS.pattern + r"|<#0\.[1-5]#>")
BAD_PAUSE = re.compile(r"^\s*<#|<#[^#]*#>\s*$|#>\s*<#")
# 句尾助词要贴着前面的字念，前面不能停顿
PAUSE_BEFORE_PARTICLE = re.compile(r"<#0\.[1-5]#>(?=[呐嘛呀啦哦呢吧喵])")
SPACED_MARK = re.compile(r"\s*(" + MARKS.pattern + r")\s*")
ANY_PAUSE = re.compile(r"<#0\.[1-5]#>")
LITERALS = re.compile(r"[0-9A-Za-z]+")
MAX_SCRIPT_TONES = 3
MAX_SCRIPT_PAUSES = 2
# 这个音色偏慢，统一提速才像日常说话；情绪只给低语，其余由 MiniMax 按文字匹配，固定情绪反而发平
# 低语只有 2.6 支持，而 2.6 不认语气词标记
MOODS = {
    "AUTO": (None, 1.15),
    "HAPPY": (None, 1.2),
    "SAD": (None, 1.1),
    "SURPRISED": (None, 1.2),
    "CALM": (None, 1.1),
    "WHISPER": ("whisper", 1.0),
}
OPENING = {"HAPPY": LAUGH, "SAD": SIGH, "SURPRISED": GASP}


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
    if mood == "WHISPER":
        spoken = TONE_CHARS.sub("", spoken)
    else:
        # 回复里没有现成的语气时，一轮的开头按情绪补一个
        if lead and mood in OPENING and not TONE_CHARS.search(spoken):
            spoken = OPENING[mood] + spoken
        if len(spoken) >= BREATH_FROM:
            spoken = FIRST_SENTENCE.sub(lambda match: match.group() + BREATH, spoken, count=1)
    # 一个气泡里语气词多了反而假，只留前两个；停顿语法只能放在两段可发音的内容之间
    kept = 0
    parts = []
    for char in spoken:
        if char in TONES:
            kept += 1
            if kept > MAX_TONES:
                continue
            parts.append(TONES[char])
        else:
            parts.append("<#0.3#>" if char == PAUSE else char)
    return "".join(parts).strip()


def unmarked(text: str) -> str:
    return re.sub(r"[^\w]|_", "", MARKS.sub("", text))


# 口语稿可以改说法，这里只查代码查得出的：标记语法合法、长度相近、数字和字母原样；有没有改意思交给复核
def vetted(script: str, base: str, mood: str) -> str | None:
    script = PAUSE_BEFORE_PARTICLE.sub("", SPACED_MARK.sub(r"\1", script.strip()))
    for pause in ANY_PAUSE.findall(script)[MAX_SCRIPT_PAUSES:]:
        head, _, tail = script.rpartition(pause)
        script = head + tail
    tags = SCRIPT_TAGS.findall(script)
    words, original = unmarked(script), unmarked(base)
    if (
        not words
        or len(tags) > MAX_SCRIPT_TONES
        or (tags and mood == "WHISPER")
        or BAD_PAUSE.search(script)
        or re.search(r"[()（）<>#]", MARKS.sub("", script))
        or not len(original) / 2 <= len(words) <= len(original) * 3 / 2 + 10
        or sorted(LITERALS.findall(words)) != sorted(LITERALS.findall(original))
    ):
        return None
    return script


class VoiceClient:
    def __init__(self, settings, http, gateway):
        self.settings, self.http, self.gateway = settings, http, gateway

    # 语音只是文字的附加：缺密钥、合成失败或没有可念的内容都返回无语音，不影响这一轮
    async def synthesize(self, spoken: str, mood: str = "AUTO") -> dict:
        key = self.settings.voice_key
        if not spoken or key is None:
            return SILENT
        emotion, speed = MOODS[mood]
        model = self.settings.voice_whisper_model if mood == "WHISPER" else self.settings.voice_model

        async def call():
            response = await self.http.post(
                self.settings.voice_url,
                headers={"Authorization": "Bearer " + key.get_secret_value()},
                timeout=self.settings.voice_timeout,
                json={
                    "model": model,
                    "text": spoken,
                    "stream": False,
                    "language_boost": "Chinese",
                    "output_format": "hex",
                    "voice_setting": {
                        "voice_id": self.settings.voice_id,
                        "speed": speed,
                        **({"emotion": emotion} if emotion else {}),
                    },
                    "audio_setting": {"format": "mp3", "bitrate": 128000, "channel": 1},
                },
            )
            response.raise_for_status()
            data = response.json()
            if data.get("base_resp", {}).get("status_code") != 0:
                raise ValueError("语音合成被拒绝")
            return data

        try:
            data = await self.gateway.invoke(
                model,
                "voice",
                len(spoken),
                call,
                lambda r: {"input_tokens": r.get("extra_info", {}).get("usage_characters", 0)},
                retry=False,
            )
            audio = bytes.fromhex(data["data"]["audio"])
            duration = int(data["extra_info"]["audio_length"])
            if not audio or duration <= 0:
                raise ValueError("语音合成结果为空")
        except Exception as error:
            LOG.warning("voice_failed error_type=%s", type(error).__name__)
            return SILENT
        return {"audio": base64.b64encode(audio).decode(), "durationMs": duration}
