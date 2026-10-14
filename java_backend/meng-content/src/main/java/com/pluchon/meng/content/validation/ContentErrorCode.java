package com.pluchon.meng.content.validation;

import com.pluchon.meng.common.core.error.ErrorCode;

// 内容域业务错误码
public enum ContentErrorCode implements ErrorCode {
    FOLDER_LIMIT("CONTENT_FOLDER_LIMIT", "收藏夹最多 20 个", 409),
    FOLDER_NAME_TAKEN("CONTENT_FOLDER_NAME_TAKEN", "收藏夹名字已使用", 409),
    FAVORITE_FOLDER_LIMIT(
        "CONTENT_FAVORITE_FOLDER_LIMIT",
        "一篇帖子最多放进 10 个收藏夹",
        409
    ),
    COMMENT_DELETED("CONTENT_COMMENT_DELETED", "评论已被删除", 404),
    REPORT_DUPLICATE("CONTENT_REPORT_DUPLICATE", "已经举报过", 409);

    // 业务错误码
    private final String code;

    // 业务错误说明
    private final String message;

    // 处理状态
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
