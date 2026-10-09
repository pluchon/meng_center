package com.pluchon.meng.user.service.security;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import java.util.Locale;
import java.util.regex.Pattern;

// 用户名的归一化与格式规则，登录、注册、重新申请和管理员初始化共用
public final class UsernamePolicy {

    // 归一化之后的用户名：小写字母、数字、下划线，3 到 32 位
    private static final Pattern FORMAT = Pattern.compile("[a-z0-9_]{3,32}");

    private UsernamePolicy() {}

    // 是否已经是合规的归一化用户名
    public static boolean valid(String username) {
        return username != null && FORMAT.matcher(username).matches();
    }

    // 去掉首尾空白并转小写，不合规按参数错误处理
    public static String normalize(String input) {
        String value = input.strip().toLowerCase(Locale.ROOT);
        if (!valid(value)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        }
        return value;
    }
}
