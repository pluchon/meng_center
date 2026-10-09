package com.pluchon.meng.user.entity.enums;

// 验证码用途，不同用途的验证码存在不同的键下
public enum VerificationPurpose {
    REGISTRATION("meng:"),
    PASSWORD_RESET("meng:password-reset:");

    private final String prefix;

    VerificationPurpose(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
