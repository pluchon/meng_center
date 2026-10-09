package com.pluchon.meng.user.controller;

public final class UserRoutes {

    public static final String ROOT = "/user";
    public static final String CAPTCHA = "/captcha";
    public static final String CAPTCHA_VERIFICATION = "/captcha/verification";
    public static final String EMAIL_CODES = "/email-codes";
    public static final String SMS_CODES = "/sms-codes";
    public static final String REGISTRATIONS = "/registrations";
    public static final String REAPPLICATION = "/registrations/reapplication";
    public static final String SESSIONS = "/sessions";
    public static final String CURRENT_SESSION = "/sessions/current";
    public static final String ME = "/me";
    public static final String PASSWORD_RESET_CODES = "/password-reset-codes";
    public static final String PASSWORD_RESETS = "/password-resets";
    public static final String PASSWORD = "/me/password";
    public static final String ADMIN_REGISTRATIONS = "/admin/registrations";
    public static final String APPROVAL = "/admin/registrations/{userId}/approval";
    public static final String REJECTION = "/admin/registrations/{userId}/rejection";
    public static final String DEFAULT_PAGE = "1";
    public static final String DEFAULT_SIZE = "20";

    private UserRoutes() {}
}
