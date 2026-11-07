package com.pluchon.meng.user.controller.dto;

import jakarta.validation.constraints.*;

// 登录后凭手机验证码重置密码的请求
public record MyPasswordResetDTO(
    @NotBlank @Pattern(regexp = "[0-9]{6}") String code,
    @NotBlank
    @Pattern(regexp = RegistrationConstraints.PASSWORD_PATTERN)
    String password,
    @NotBlank @Size(max = 20) String confirmPassword
) {
    @Override
    public String toString() {
        return "MyPasswordResetDTO[redacted]";
    }
}
