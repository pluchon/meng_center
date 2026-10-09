package com.pluchon.meng.common.core.security;

// 定位一个会话所需的最小信息
public record SessionKey(long userId, String sessionId) {
    public SessionKey {
        if (userId < 1 || sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Invalid session key");
        }
    }
}
