package com.pluchon.meng.common.web.context;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import java.util.Optional;

/** Bound to the servlet request thread only; never propagated to MQ or async work. */
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
