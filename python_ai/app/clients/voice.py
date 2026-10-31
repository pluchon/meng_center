from __future__ import annotations

import base64
import logging
import re

LOG = logging.getLogger(__name__)
# 先把语气换成私用区占位符再清洗，回复文字里自带的 (laughs)、<#x#> 不会被当成控制语法
LAUGH, SIGH, EXHALE, HUM, INHALE, EMM, GASP, BREATH, PAUSE = ""
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
UNSPOKEN = re.compile(r"[^\w\s，。！？、；：,.!?;:~～-]|_")
PLACEHOLDER = re.compile(r"[-]")
TONE_CHARS = re.compile(r"[-]")
AFTER_TONE = re.compile(r"([-])[，,、\s]+")
PAUSES = re.compile(r"(?:[\s，,、。]*)*")
# 第一句说完换一口气；太短的气泡不换，后面没有话也不换
FIRST_SENTENCE = re.compile(r"[。！？!?]+(?=[^\s-])")
BREATH_FROM = 30
MAX_TONES = 2
SILENT = {"audio": None, "durationMs": 0}
# 情绪、语速。AUTO 不传情绪，由 MiniMax 按文字匹配；低语只有 2.6 支持，而 2.6 不认语气词标记
MOODS = {
    "AUTO": (None, 0.95),
    "HAPPY": ("happy", 1.0),
    "SAD": ("sad", 0.9),
    "SURPRISED": ("surprised", 1.0),
    "CALM": ("calm", 0.92),
    "WHISPER": ("whisper", 0.9),
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


class VoiceClient:
    def __init__(self, settings, http, gateway):
        self.settings, self.http, self.gateway = settings, http, gateway

    # 语音只是文字的附加：缺密钥、合成失败或没有可念的内容都返回无语音，不影响这一轮
    async def synthesize(self, text: str, mood: str = "AUTO", lead: bool = False) -> dict:
        spoken, key = speakable(text, mood, lead), self.settings.voice_key
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
                    "voice_modify": {
                        "intensity": self.settings.voice_intensity,
                        "timbre": self.settings.voice_timbre,
                    },
                    "audio_setting": {"format": "mp3", "bitrate": 64000, "channel": 1},
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
