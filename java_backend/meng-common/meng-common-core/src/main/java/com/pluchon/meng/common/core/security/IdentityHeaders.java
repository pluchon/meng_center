package com.pluchon.meng.common.core.security;

// 网关写给下游服务的身份请求头，下游只信这些头
public final class IdentityHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";
    public static final String SESSION_ID = "X-Session-Id";
    public static final String CLIENT_IP = "X-Client-IP";

    private IdentityHeaders() {}
}
