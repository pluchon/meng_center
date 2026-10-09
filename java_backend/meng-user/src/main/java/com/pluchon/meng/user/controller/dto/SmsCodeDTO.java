package com.pluchon.meng.user.controller.dto;

import jakarta.validation.constraints.*;

public record SmsCodeDTO(
    @NotBlank @Size(max = 5) String countryCode,
    @NotBlank @Size(max = 32) String phone,
    @NotBlank String ticket
) {
    @Override
    public String toString() {
        return "SmsCodeDTO[redacted]";
    }
}
