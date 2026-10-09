package com.pluchon.meng.user.controller.dto;

import jakarta.validation.constraints.*;

public record CaptchaVerificationDTO(
    @NotBlank String id,
    @NotNull cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack track
) {
    @Override
    public String toString() {
        return "CaptchaVerificationDTO[redacted]";
    }
}
