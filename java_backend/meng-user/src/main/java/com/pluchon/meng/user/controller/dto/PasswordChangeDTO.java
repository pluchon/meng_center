package com.pluchon.meng.user.controller.dto;

import jakarta.validation.constraints.*;

public record PasswordChangeDTO(
    @NotBlank @Size(max = 72) String oldPassword,
    @NotBlank @Size(min = 8, max = 72) String password,
    @NotBlank String confirmPassword
) {
    @Override
    public String toString() {
        return "PasswordChangeDTO[redacted]";
    }
}
