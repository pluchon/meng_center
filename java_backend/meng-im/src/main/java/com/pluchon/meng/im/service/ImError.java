package com.pluchon.meng.im.service;

import com.pluchon.meng.common.core.error.ErrorCode;

// 私信受限时使用明确的业务码
public enum ImError implements ErrorCode {
    BLOCKED("IM_BLOCKED", "对方暂时不接收私信", 403),
    WAIT_REPLY("IM_WAIT_REPLY", "请等待对方回复后再发送", 403),
    PEER_NOT_FOUND("IM_PEER_NOT_FOUND", "对方暂时不可用", 404),
    SELF("IM_SELF", "不能给自己发送私信", 409),
    REVOKE_EXPIRED("IM_REVOKE_EXPIRED", "已超过 5 分钟，不能撤回了", 409);

    private final String code;
    private final String message;
    private final int status;

    ImError(String code, String message, int status) {
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
