package com.pluchon.meng.user.controller.dto;

import jakarta.validation.constraints.Size;

public record ContactChangeDTO(
    @Size(max = 254) String email,
    @Size(max = 4) String countryCode,
    @Size(max = 32) String phone,
    @Size(max = 64) String ticket,
    @Size(max = 8) String code,
    @Size(max = 72) String password
) {
    @Override
    public String toString() {
        return "ContactChangeDTO[redacted]";
    }
}
