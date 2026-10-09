package com.pluchon.meng.common.web.context;

public record RequestIdentity(long userId, String role, String sessionId) {
    public RequestIdentity(long userId, String role) {
        this(userId, role, null);
    }
}
