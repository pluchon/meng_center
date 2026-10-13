package com.pluchon.meng.user.controller.dto;

import jakarta.validation.constraints.*;

// 修改密码请求
public record PasswordChangeDTO(
    @NotBlank @Size(max = 72) String oldPassword,
    @NotBlank
    @Pattern(regexp = RegistrationConstraints.PASSWORD_PATTERN)
    String password,
    @NotBlank @Size(max = 20) String confirmPassword
) {
    @Override
    public String toString() {
        return "PasswordChangeDTO[redacted]";
    }
}
