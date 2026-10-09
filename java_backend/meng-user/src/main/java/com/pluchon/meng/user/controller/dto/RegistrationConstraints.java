package com.pluchon.meng.user.controller.dto;

// 注册相关请求共用的校验规则
public final class RegistrationConstraints {
    public static final int APPLY_REASON_MAX_LENGTH = 100;
    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9]{8,20}$";

    private RegistrationConstraints() {}
}
