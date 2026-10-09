package com.pluchon.meng.user.controller.dto;

import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import jakarta.validation.constraints.*;

// 滑块校验请求：题目编号和拖动轨迹
public record CaptchaVerificationDTO(
    @NotBlank String id,
    @NotNull ImageCaptchaTrack track
) {
    @Override
    public String toString() {
        return "CaptchaVerificationDTO[redacted]";
    }
}
