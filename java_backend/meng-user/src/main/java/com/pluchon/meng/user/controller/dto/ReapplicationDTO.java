package com.pluchon.meng.user.controller.dto;

import jakarta.validation.constraints.*;

public record ReapplicationDTO(
    @NotBlank @Size(max = 32) String username,
    @NotBlank @Size(max = 72) String password,
    @NotBlank String ticket,
    @Size(max = RegistrationConstraints.APPLY_REASON_MAX_LENGTH) String applyReason
) {
    @Override
    public String toString() {
        return "ReapplicationDTO[redacted]";
    }
}
