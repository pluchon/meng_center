package com.pluchon.meng.companion.service;

import com.pluchon.meng.common.core.error.ErrorCode;

// 对话状态使用独立业务码，便于前端区分限次、冷却和计算故障
public enum CompanionError implements ErrorCode {
    COMPANION_COOLDOWN(429, "先休息一下，稍后再聊"),
    COMPANION_QUOTA_EXCEEDED(429, "本时段可用次数已用完"),
    COMPANION_BUSY(409, "上一轮对话尚未结束"),
    AI_UNAVAILABLE(503, "萌萌们去休息了"),
    COMPANION_SESSION_LIMIT(409, "会话已达上限，请先删除旧会话");

    private final int status;
    private final String message;
    CompanionError(int status, String message) { this.status = status; this.message = message; }
    public String code() { return name(); }
    public String message() { return message; }
    public int httpStatus() { return status; }
}
