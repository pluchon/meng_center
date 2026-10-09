package com.pluchon.meng.common.core.response;

import com.pluchon.meng.common.core.error.ErrorCode;

public record ApiResponse<T>(String code, String message, T data) {
    public static final String SUCCESS_CODE = "OK";

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, null, data);
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, T data) {
        return new ApiResponse<>(errorCode.code(), errorCode.message(), data);
    }
}
