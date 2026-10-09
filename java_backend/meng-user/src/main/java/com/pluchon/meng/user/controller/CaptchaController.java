package com.pluchon.meng.user.controller;

import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.common.core.security.IdentityHeaders;
import com.pluchon.meng.user.controller.dto.CaptchaVerificationDTO;
import com.pluchon.meng.user.service.CaptchaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(UserRoutes.ROOT)
public final class CaptchaController {

    private final CaptchaService service;

    public CaptchaController(CaptchaService service) {
        this.service = service;
    }

    @PostMapping(UserRoutes.CAPTCHA)
    public ApiResponse<ImageCaptchaVO> generate() {
        return ApiResponse.success(service.generate());
    }

    @PostMapping(UserRoutes.CAPTCHA_VERIFICATION)
    public ApiResponse<CaptchaService.Ticket> verify(
        @Valid @RequestBody CaptchaVerificationDTO request,
        @RequestHeader(IdentityHeaders.CLIENT_IP) String ip
    ) {
        return ApiResponse.success(service.verify(request.id(), request.track(), ip));
    }
}
