package com.pluchon.meng.user.controller.dto;

import jakarta.validation.constraints.*;

// 登录后忘记原密码，给绑定的手机号发验证码的请求
public record MyPasswordResetCodeDTO(@NotBlank String ticket) {
    @Override
    public String toString() {
        return "MyPasswordResetCodeDTO[redacted]";
    }
}
