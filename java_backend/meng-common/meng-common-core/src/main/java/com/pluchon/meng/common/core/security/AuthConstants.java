package com.pluchon.meng.common.core.security;

// 鉴权相关的常量：Cookie 名、会话键前缀和密钥所在的环境变量名
public final class AuthConstants {

    public static final String TOKEN_COOKIE = "meng_token";
    public static final String ADMIN_ROLE = "ADMIN";
    public static final String NORMAL_STATUS = "NORMAL";
    public static final String SESSION_PREFIX = "meng:session:";
    public static final String USER_SESSIONS_PREFIX = "meng:user-sessions:";
    public static final String SESSION_USER_ID = "userId";
    public static final String SESSION_ROLE = "role";
    public static final String SESSION_STATUS = "status";
    public static final String JWT_SECRET_ENV = "MENG_JWT_SECRET";
    public static final String PII_KEY_ENV = "MENG_PII_ENC_KEY";
    public static final String PII_HASH_KEY_ENV = "MENG_PII_HASH_KEY";

    private AuthConstants() {}
}
