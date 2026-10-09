package com.pluchon.meng.common.core.error;

public interface ErrorCode {
    String code();
    String message();
    int httpStatus();
}
