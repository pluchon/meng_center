package com.pluchon.meng.user.controller;

import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.common.core.security.IdentityHeaders;
import com.pluchon.meng.user.controller.dto.*;
import com.pluchon.meng.user.controller.vo.UserVO;
import com.pluchon.meng.user.service.*;
import com.pluchon.meng.user.service.security.TokenCookieService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(UserRoutes.ROOT)
public final class SessionController {

    private final SessionAuthService sessions;
    private final UserQueryService users;
    private final TokenCookieService cookies;

    public SessionController(SessionAuthService sessions, UserQueryService users, TokenCookieService cookies) {
        this.sessions = sessions;
        this.users = users;
        this.cookies = cookies;
    }

    @PostMapping(UserRoutes.SESSIONS)
    public ResponseEntity<ApiResponse<Void>> login(
        @Valid @RequestBody LoginDTO request,
        @RequestHeader(IdentityHeaders.CLIENT_IP) String ip
    ) {
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookies.issue(sessions.login(request, ip)))
            .body(ApiResponse.success(null));
    }

    @DeleteMapping(UserRoutes.CURRENT_SESSION)
    public ResponseEntity<ApiResponse<Void>> logout() {
        sessions.logout();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookies.clear()).body(ApiResponse.success(null));
    }

    @GetMapping(UserRoutes.ME)
    public ApiResponse<UserVO> me() {
        return ApiResponse.success(users.me());
    }

    @PutMapping(UserRoutes.PASSWORD)
    public ResponseEntity<ApiResponse<Void>> password(@Valid @RequestBody PasswordChangeDTO request) {
        sessions.changePassword(request);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookies.clear()).body(ApiResponse.success(null));
    }
}
