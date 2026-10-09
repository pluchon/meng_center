package com.pluchon.meng.user.controller;

import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.common.core.security.IdentityHeaders;
import com.pluchon.meng.user.controller.dto.*;
import com.pluchon.meng.user.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(UserRoutes.ROOT)
public final class PasswordResetController {

    private final CodeDeliveryService codes;
    private final PasswordResetService passwords;

    public PasswordResetController(
        CodeDeliveryService codes,
        PasswordResetService passwords
    ) {
        this.codes = codes;
        this.passwords = passwords;
    }

    @PostMapping(UserRoutes.PASSWORD_RESET_CODES)
    public ApiResponse<Void> code(
        @Valid @RequestBody PasswordResetCodeDTO request,
        @RequestHeader(IdentityHeaders.CLIENT_IP) String ip
    ) {
        codes.resetCode(
            request.email(),
            request.countryCode(),
            request.phone(),
            request.ticket(),
            ip
        );
        return ApiResponse.success(null);
    }

    @PostMapping(UserRoutes.PASSWORD_RESETS)
    public ApiResponse<Void> reset(
        @Valid @RequestBody PasswordResetDTO request,
        @RequestHeader(IdentityHeaders.CLIENT_IP) String ip
    ) {
        passwords.reset(request, ip);
        return ApiResponse.success(null);
    }
}
