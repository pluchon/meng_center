package com.pluchon.meng.common.core.security;

// 网关注入身份头使用的角色，业务服务不依赖用户服务的实体枚举
public enum IdentityRole {
    USER,
    ADMIN
}
