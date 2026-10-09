package com.pluchon.meng.user.controller;

import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.common.core.security.IdentityHeaders;
import com.pluchon.meng.user.controller.dto.EmailCodeDTO;
import com.pluchon.meng.user.controller.dto.ReapplicationDTO;
import com.pluchon.meng.user.controller.dto.RegistrationDTO;
import com.pluchon.meng.user.controller.dto.SmsCodeDTO;
import com.pluchon.meng.user.service.CodeDeliveryService;
import com.pluchon.meng.user.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 注册申请接口
@RestController
@RequestMapping(UserRoutes.ROOT)
public final class RegistrationController {

    @Autowired
    private RegistrationService registrations;

    @Autowired
    private CodeDeliveryService codes;

    /** 发注册用的邮箱验证码 */
    @PostMapping(UserRoutes.EMAIL_CODES)
    public ApiResponse<Void> email(
        @Valid @RequestBody EmailCodeDTO request,
        @RequestHeader(IdentityHeaders.CLIENT_IP) String ip
    ) {
        codes.email(request.email(), request.ticket(), ip);
        return ApiResponse.success(null);
    }

    /** 发注册用的短信验证码 */
    @PostMapping(UserRoutes.SMS_CODES)
    public ApiResponse<Void> sms(
        @Valid @RequestBody SmsCodeDTO request,
        @RequestHeader(IdentityHeaders.CLIENT_IP) String ip
    ) {
        codes.sms(request.countryCode(), request.phone(), request.ticket(), ip);
        return ApiResponse.success(null);
    }

    /** 提交注册申请 */
    @PostMapping(UserRoutes.REGISTRATIONS)
    public ApiResponse<Void> register(
        @Valid @RequestBody RegistrationDTO request,
        @RequestHeader(IdentityHeaders.CLIENT_IP) String ip
    ) {
        registrations.register(request, ip);
        return ApiResponse.success(null);
    }

    /** 被拒后重新申请 */
    @PostMapping(UserRoutes.REAPPLICATION)
    public ApiResponse<Void> reapply(
        @Valid @RequestBody ReapplicationDTO request,
        @RequestHeader(IdentityHeaders.CLIENT_IP) String ip
    ) {
        registrations.reapply(request, ip);
        return ApiResponse.success(null);
    }
}
