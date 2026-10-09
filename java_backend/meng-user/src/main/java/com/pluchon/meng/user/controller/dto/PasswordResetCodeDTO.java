package com.pluchon.meng.user.controller.dto;

import jakarta.validation.constraints.*;

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
