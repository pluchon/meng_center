package com.pluchon.meng.common.web.context;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import java.util.Optional;

// 当前请求的身份，只绑定在请求线程上，不会带进消息消费和异步线程
public final class UserContext {

    private static final ThreadLocal<RequestIdentity> CURRENT = new ThreadLocal<>();

    private UserContext() {}

    public static Optional<RequestIdentity> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static RequestIdentity require() {
        return current().orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHENTICATED));
    }

    public static void bind(RequestIdentity identity) {
        CURRENT.set(identity);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
