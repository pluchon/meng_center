package com.pluchon.meng.user.controller.dto;

import jakarta.validation.constraints.*;

// 拒绝申请请求
public record RejectionDTO(@NotBlank @Size(max = 200) String reason) {
    @Override
    public String toString() {
        return "RejectionDTO[redacted]";
    }
}
