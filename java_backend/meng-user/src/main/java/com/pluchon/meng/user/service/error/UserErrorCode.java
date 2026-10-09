package com.pluchon.meng.user.service.error;

import com.pluchon.meng.common.core.error.ErrorCode;

public enum UserErrorCode implements ErrorCode {
    INVALID_CREDENTIALS("USER_INVALID_CREDENTIALS", "账号或密码错误", 401),
    PENDING("USER_PENDING", "申请仍在审核中", 403),
    REJECTED("USER_REJECTED", "申请未通过审核", 403),
    CAPTCHA_REQUIRED("USER_CAPTCHA_REQUIRED", "请完成滑块验证", 400),
    INVALID_TICKET("USER_INVALID_TICKET", "滑块验证已失效", 400),
    INVALID_CODE("USER_INVALID_CODE", "验证码不正确或已失效", 400),
    RESET_FAILED("USER_RESET_FAILED", "重置验证失败，请检查联系方式与验证码", 400),
    CONTACT_REQUIRED("USER_CONTACT_REQUIRED", "邮箱和手机号请选择一种填写", 400),
    CONTACT_INVALID("USER_CONTACT_INVALID", "联系方式格式不正确", 400),
    USERNAME_TAKEN("USER_USERNAME_TAKEN", "用户名已被使用", 409),
    CONTACT_TAKEN("USER_CONTACT_TAKEN", "联系方式已被使用", 409),
    PASSWORD_MISMATCH("USER_PASSWORD_MISMATCH", "两次输入的密码不一致", 400),
    DELIVERY_FAILED("USER_DELIVERY_FAILED", "验证码发送失败，请稍后重试", 503),
    SMS_UNAVAILABLE("USER_SMS_UNAVAILABLE", "短信通道尚未配置", 503),
    LEGAL_VERSION_CHANGED("USER_LEGAL_VERSION_CHANGED", "协议已更新，请重新阅读并同意", 409),
    LEGAL_NOT_PUBLISHED("USER_LEGAL_NOT_PUBLISHED", "正式协议尚未发布，暂不开放注册", 503);

    private final String code;
    private final String message;
    private final int status;

    UserErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public int httpStatus() {
        return status;
    }
}
