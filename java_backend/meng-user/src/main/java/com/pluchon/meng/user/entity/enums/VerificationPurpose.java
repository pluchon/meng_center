package com.pluchon.meng.user.entity.enums;

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
