package com.pluchon.meng.user.service.security;

import com.pluchon.meng.common.core.security.AuthConstants;
import com.pluchon.meng.user.config.UserProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

// 令牌只通过 HttpOnly Cookie 下发，脚本读不到
@Service
public final class TokenCookieService {

    private static final String SAME_SITE = "Lax";
    private static final String PATH = "/";

    @Autowired
    private UserProperties properties;

    private ResponseCookie.ResponseCookieBuilder cookie(String value) {
        return ResponseCookie.from(AuthConstants.TOKEN_COOKIE, value)
            .httpOnly(true)
            .secure(properties.cookie().secure())
            .sameSite(SAME_SITE)
            .path(PATH);
    }

    // 登录成功时写入令牌，会话期 Cookie，有效期由服务端会话控制
    public String issue(String token) {
        return cookie(token).build().toString();
    }

    // 退出或改密后让浏览器立即丢弃令牌
    public String clear() {
        return cookie("").maxAge(0).build().toString();
    }
}
