package com.pluchon.meng.user.controller.dto;

public final class RegistrationConstraints {
    public static final int APPLY_REASON_MAX_LENGTH = 100;
    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9]{8,20}$";

    private RegistrationConstraints() {}
}
