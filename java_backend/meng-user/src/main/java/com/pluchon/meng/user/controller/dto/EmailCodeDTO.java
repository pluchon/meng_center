package com.pluchon.meng.user.controller.dto;

import jakarta.validation.constraints.*;

// 发邮箱验证码请求
public record EmailCodeDTO(@NotBlank @Size(max = 254) String email, @NotBlank String ticket) {
    @Override
    public String toString() {
        return "EmailCodeDTO[redacted]";
    }
}
