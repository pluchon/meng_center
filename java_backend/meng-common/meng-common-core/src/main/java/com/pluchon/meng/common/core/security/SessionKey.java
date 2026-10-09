package com.pluchon.meng.common.core.security;

public record SessionKey(long userId, String sessionId) {
    public SessionKey {
        if (userId < 1 || sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Invalid session key");
        }
    }
}
