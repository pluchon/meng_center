package com.pluchon.meng.user.controller.dto;

import jakarta.validation.constraints.*;

public record EmailCodeDTO(@NotBlank @Size(max = 254) String email, @NotBlank String ticket) {
    @Override
    public String toString() {
        return "EmailCodeDTO[redacted]";
    }
}
