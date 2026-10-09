package com.pluchon.meng.common.core.error;

import java.util.Objects;

// 业务异常：带错误码，可附带给前端的数据，由统一异常处理转成响应
public final class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object data;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, Object data) {
        super(Objects.requireNonNull(errorCode).message());
        this.errorCode = errorCode;
        this.data = data;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Object data() {
        return data;
    }
}
