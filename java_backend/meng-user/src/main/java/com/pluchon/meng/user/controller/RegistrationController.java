package com.pluchon.meng.user.controller;

import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.common.core.security.IdentityHeaders;
import com.pluchon.meng.user.controller.dto.*;
import com.pluchon.meng.user.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(UserRoutes.ROOT)
public final class RegistrationController {

    private final RegistrationService registrations;
    private final CodeDeliveryService codes;

    public RegistrationController(RegistrationService registrations, CodeDeliveryService codes) {
        this.registrations = registrations;
        this.codes = codes;
    }

    @PostMapping(UserRoutes.EMAIL_CODES)
    public ApiResponse<Void> email(
        @Valid @RequestBody EmailCodeDTO request,
        @RequestHeader(IdentityHeaders.CLIENT_IP) String ip
    ) {
        codes.email(request.email(), request.ticket(), ip);
        return ApiResponse.success(null);
    }

    @PostMapping(UserRoutes.SMS_CODES)
    public ApiResponse<Void> sms(
        @Valid @RequestBody SmsCodeDTO request,
        @RequestHeader(IdentityHeaders.CLIENT_IP) String ip
    ) {
        codes.sms(request.countryCode(), request.phone(), request.ticket(), ip);
        return ApiResponse.success(null);
    }

    @PostMapping(UserRoutes.REGISTRATIONS)
    public ApiResponse<Void> register(
        @Valid @RequestBody RegistrationDTO request,
        @RequestHeader(IdentityHeaders.CLIENT_IP) String ip
    ) {
        registrations.register(request, ip);
        return ApiResponse.success(null);
    }

    @PostMapping(UserRoutes.REAPPLICATION)
    public ApiResponse<Void> reapply(
        @Valid @RequestBody ReapplicationDTO request,
        @RequestHeader(IdentityHeaders.CLIENT_IP) String ip
    ) {
        registrations.reapply(request, ip);
        return ApiResponse.success(null);
    }
}
