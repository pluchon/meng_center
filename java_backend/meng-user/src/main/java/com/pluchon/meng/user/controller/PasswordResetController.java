package com.pluchon.meng.user.controller;

import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.common.core.security.IdentityHeaders;
import com.pluchon.meng.user.controller.dto.MyPasswordResetCodeDTO;
import com.pluchon.meng.user.controller.dto.MyPasswordResetDTO;
import com.pluchon.meng.user.controller.dto.PasswordResetCodeDTO;
import com.pluchon.meng.user.controller.dto.PasswordResetDTO;
import com.pluchon.meng.user.controller.vo.ResetCodeVO;
import com.pluchon.meng.user.service.CodeDeliveryService;
import com.pluchon.meng.user.service.PasswordResetService;
import com.pluchon.meng.user.service.security.TokenCookieService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 重置密码接口
@RestController
@RequestMapping(UserRoutes.ROOT)
public final class PasswordResetController {

    @Autowired
    private CodeDeliveryService codes;

    @Autowired
    private PasswordResetService passwords;

    @Autowired
    private TokenCookieService cookies;

    /** 发重置密码用的验证码 */
    @PostMapping(UserRoutes.PASSWORD_RESET_CODES)
    public ApiResponse<Void> code(
        @Valid @RequestBody PasswordResetCodeDTO request,
        @RequestHeader(IdentityHeaders.CLIENT_IP) String ip
    ) {
        codes.resetCode(request.email(), request.countryCode(), request.phone(), request.ticket(), ip);
        return ApiResponse.success(null);
    }

    /** 凭验证码重置密码 */
    @PostMapping(UserRoutes.PASSWORD_RESETS)
    public ApiResponse<Void> reset(
        @Valid @RequestBody PasswordResetDTO request,
        @RequestHeader(IdentityHeaders.CLIENT_IP) String ip
    ) {
        passwords.reset(request, ip);
        return ApiResponse.success(null);
    }

    /** 登录后忘记原密码，给绑定的手机号发验证码 */
    @PostMapping(UserRoutes.MY_PASSWORD_RESET_CODES)
    public ApiResponse<ResetCodeVO> myCode(
        @Valid @RequestBody MyPasswordResetCodeDTO request,
        @RequestHeader(IdentityHeaders.CLIENT_IP) String ip
    ) {
        return ApiResponse.success(new ResetCodeVO(passwords.sendMine(request.ticket(), ip)));
    }

    /** 登录后凭手机验证码重置密码并让所有设备下线 */
    @PostMapping(UserRoutes.MY_PASSWORD_RESETS)
    public ResponseEntity<ApiResponse<Void>> myReset(@Valid @RequestBody MyPasswordResetDTO request) {
        passwords.resetMine(request);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookies.clear())
            .body(ApiResponse.success(null));
    }
}
