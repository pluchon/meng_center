package com.pluchon.meng.user.controller;

import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.common.core.security.IdentityHeaders;
import com.pluchon.meng.user.controller.dto.LoginDTO;
import com.pluchon.meng.user.controller.dto.PasswordChangeDTO;
import com.pluchon.meng.user.controller.vo.UserVO;
import com.pluchon.meng.user.service.SessionAuthService;
import com.pluchon.meng.user.service.UserQueryService;
import com.pluchon.meng.user.service.security.TokenCookieService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 登录会话与当前账号接口
@RestController
@RequestMapping(UserRoutes.ROOT)
public final class SessionController {

    @Autowired
    private SessionAuthService sessions;

    @Autowired
    private UserQueryService users;

    @Autowired
    private TokenCookieService cookies;

    /** 登录并写入令牌 Cookie */
    @PostMapping(UserRoutes.SESSIONS)
    public ResponseEntity<ApiResponse<Void>> login(
        @Valid @RequestBody LoginDTO request,
        @RequestHeader(IdentityHeaders.CLIENT_IP) String ip,
        @RequestHeader(
            value = HttpHeaders.USER_AGENT,
            defaultValue = ""
        ) String agent
    ) {
        return withCookie(cookies.issue(sessions.login(request, ip, agent)));
    }

    /** 退出当前设备 */
    @DeleteMapping(UserRoutes.CURRENT_SESSION)
    public ResponseEntity<ApiResponse<Void>> logout() {
        sessions.logout();
        return withCookie(cookies.clear());
    }

    /** 查询当前登录账号 */
    @GetMapping(UserRoutes.ME)
    public ApiResponse<UserVO> me() {
        return ApiResponse.success(users.me());
    }

    /** 修改密码并让所有设备下线 */
    @PutMapping(UserRoutes.PASSWORD)
    public ResponseEntity<ApiResponse<Void>> password(
        @Valid @RequestBody PasswordChangeDTO request
    ) {
        sessions.changePassword(request);
        return withCookie(cookies.clear());
    }

    private ResponseEntity<ApiResponse<Void>> withCookie(String cookie) {
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie)
            .body(ApiResponse.success(null));
    }
}
