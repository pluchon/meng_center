package com.pluchon.meng.common.core.error;

public enum CommonErrorCode implements ErrorCode {
    INVALID_ARGUMENT("COMMON_INVALID_ARGUMENT", "参数不合法", 400),
    UNAUTHENTICATED("COMMON_UNAUTHENTICATED", "请先登录", 401),
    FORBIDDEN("COMMON_FORBIDDEN", "无权执行此操作", 403),
    NOT_FOUND("COMMON_NOT_FOUND", "资源不存在", 404),
    METHOD_NOT_ALLOWED("COMMON_METHOD_NOT_ALLOWED", "请求方法不支持", 405),
    CONFLICT("COMMON_CONFLICT", "当前状态不允许此操作", 409),
    RATE_LIMITED("COMMON_RATE_LIMITED", "操作过于频繁", 429),
    INTERNAL_ERROR("COMMON_INTERNAL_ERROR", "服务异常", 500);

    private final String code;
    private final String message;
    private final int httpStatus;

    CommonErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
