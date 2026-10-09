package com.pluchon.meng.user.controller.dto;

import jakarta.validation.constraints.*;

public record RegistrationDTO(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{3,32}") String username,
    @NotBlank @Pattern(regexp = RegistrationConstraints.PASSWORD_PATTERN) String password,
    @NotBlank String confirmPassword,
    @Size(max = 254) String email,
    @Size(max = 5) String countryCode,
    @Size(max = 32) String phone,
    @NotBlank @Size(max = 8) String code,
    @NotBlank String ticket,
    @Size(max = RegistrationConstraints.APPLY_REASON_MAX_LENGTH) String applyReason,
    @NotBlank String termsVersion,
    @NotBlank String privacyVersion
) {
    @Override
    public String toString() {
        return "RegistrationDTO[redacted]";
    }
}
