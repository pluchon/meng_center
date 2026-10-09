package com.pluchon.meng.common.web.context;

// 网关注入的请求身份
public record RequestIdentity(long userId, String role, String sessionId) {}
