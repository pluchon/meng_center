package com.pluchon.meng.user.controller.dto;

import jakarta.validation.constraints.*;

public record LoginDTO(
    @NotBlank @Size(max = 32) String username,
    @NotBlank @Size(max = 72) String password,
    String ticket
) {
    @Override
    public String toString() {
        return "LoginDTO[redacted]";
    }
}
