package com.pluchon.meng.common.core.error;

// 错误码约定：业务码、给用户看的文案和对应的 HTTP 状态
public interface ErrorCode {
    String code();
    String message();
    int httpStatus();
}
