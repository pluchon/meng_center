package com.pluchon.meng.user.controller.dto;

import jakarta.validation.constraints.*;

// 重置密码请求，邮箱和手机号二选一
public record PasswordResetDTO(
    @Size(max = 254) String email,
    @Size(max = 5) String countryCode,
    @Size(max = 32) String phone,
    @NotBlank @Pattern(regexp = "[0-9]{6}") String code,
    @NotBlank
    @Pattern(regexp = RegistrationConstraints.PASSWORD_PATTERN)
    String password,
    @NotBlank
    @Pattern(regexp = RegistrationConstraints.PASSWORD_PATTERN)
    String confirmPassword,
    @NotBlank String ticket
) {
    @Override
    public String toString() {
        return "PasswordResetDTO[redacted]";
    }
}
