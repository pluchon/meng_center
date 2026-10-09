package com.pluchon.meng.user.controller.dto;

import jakarta.validation.constraints.*;

// 发重置密码验证码请求，邮箱和手机号二选一
public record PasswordResetCodeDTO(
    @Size(max = 254) String email,
    @Size(max = 5) String countryCode,
    @Size(max = 32) String phone,
    @NotBlank String ticket
) {
    @Override
    public String toString() {
        return "PasswordResetCodeDTO[redacted]";
    }
}
