package com.pluchon.meng.user.controller;

import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.common.core.security.IdentityHeaders;
import com.pluchon.meng.user.controller.dto.CaptchaVerificationDTO;
import com.pluchon.meng.user.controller.vo.CaptchaTicketVO;
import com.pluchon.meng.user.service.CaptchaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 滑块验证接口
@RestController
@RequestMapping(UserRoutes.ROOT)
public final class CaptchaController {

    @Autowired
    private CaptchaService service;

    /** 生成一道滑块题 */
    @PostMapping(UserRoutes.CAPTCHA)
    public ApiResponse<ImageCaptchaVO> generate() {
        return ApiResponse.success(service.generate());
    }

    /** 校验滑块并换取一次性票据 */
    @PostMapping(UserRoutes.CAPTCHA_VERIFICATION)
    public ApiResponse<CaptchaTicketVO> verify(
        @Valid @RequestBody CaptchaVerificationDTO request,
        @RequestHeader(IdentityHeaders.CLIENT_IP) String ip
    ) {
        return ApiResponse.success(service.verify(request.id(), request.track(), ip));
    }
}
