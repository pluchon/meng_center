package com.pluchon.meng.api.user.event;

// 会话被撤销事件：sessionId 非空时只撤销这一个；为空时撤销该用户全部会话，keepSessionId 非空则保留它
public record SessionsRevokedEvent(long userId, String sessionId, String keepSessionId) {
    public static final String EVENT_TYPE = "user.sessions-revoked";
    public static final String EXCHANGE = "meng.user.topic";

    // 退出或踢掉单个设备
    public static SessionsRevokedEvent one(long userId, String sessionId) {
        return new SessionsRevokedEvent(userId, sessionId, null);
    }

    // 改密、重置密码后全部下线
    public static SessionsRevokedEvent all(long userId) {
        return new SessionsRevokedEvent(userId, null, null);
    }

    // 退出其他设备，保留当前会话
    public static SessionsRevokedEvent others(long userId, String keepSessionId) {
        return new SessionsRevokedEvent(userId, null, keepSessionId);
    }

    // 给定会话是否在这次撤销范围内
    public boolean covers(String candidate) {
        if (sessionId != null) return sessionId.equals(candidate);
        return keepSessionId == null || !keepSessionId.equals(candidate);
    }
}
