package com.pluchon.meng.content.entity.enums;

import com.pluchon.meng.common.core.error.ErrorCode;

// AI 辅助功能的失败语义
public enum AiErrorCode implements ErrorCode {
    UNAVAILABLE("AI_UNAVAILABLE", "AI 暂时没空", 503),
    REJECTED("AI_CONTENT_REJECTED", "这份内容暂时无法生成封面", 422);

    private final String code;
    private final String message;
    private final int status;

    AiErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public int httpStatus() {
        return status;
    }
}
