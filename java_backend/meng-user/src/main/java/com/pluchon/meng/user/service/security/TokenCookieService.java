package com.pluchon.meng.user.service.security;

import com.pluchon.meng.common.core.security.AuthConstants;
import com.pluchon.meng.user.config.UserProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public final class TokenCookieService {

    private final UserProperties properties;

    public TokenCookieService(UserProperties properties) {
        this.properties = properties;
    }

    public String issue(String token) {
        return ResponseCookie.from(AuthConstants.TOKEN_COOKIE, token)
            .httpOnly(true)
            .secure(properties.cookie().secure())
            .sameSite("Lax")
            .path("/")
            .build()
            .toString();
    }

    public String clear() {
        return ResponseCookie.from(AuthConstants.TOKEN_COOKIE, "")
            .httpOnly(true)
            .secure(properties.cookie().secure())
            .sameSite("Lax")
            .path("/")
            .maxAge(0)
            .build()
            .toString();
    }
}
