package com.pluchon.meng.content.validation;

import com.pluchon.meng.common.core.error.ErrorCode;

public enum ContentErrorCode implements ErrorCode {
    COMMENT_DELETED("CONTENT_COMMENT_DELETED", "评论已被删除", 404),
    REPORT_DUPLICATE("CONTENT_REPORT_DUPLICATE", "已经举报过", 409);
    private final String code, message;
    private final int status;

    ContentErrorCode(String code, String message, int status) {
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
