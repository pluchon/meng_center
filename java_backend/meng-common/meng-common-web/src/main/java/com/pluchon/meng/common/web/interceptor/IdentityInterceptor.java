package com.pluchon.meng.common.web.interceptor;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.core.security.IdentityHeaders;
import com.pluchon.meng.common.web.context.RequestIdentity;
import com.pluchon.meng.common.web.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

// 把网关写入的身份头绑定到当前请求线程，请求结束时清理
public final class IdentityInterceptor implements AsyncHandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserContext.clear();
        String userId = request.getHeader(IdentityHeaders.USER_ID);
        String role = request.getHeader(IdentityHeaders.USER_ROLE);
        if (userId == null && role == null) {
            return true;
        }
        try {
            long id = Long.parseLong(userId);
            if (id < 1 || role == null || role.isBlank()) {
                throw new IllegalArgumentException();
            }
            UserContext.bind(new RequestIdentity(id, role, request.getHeader(IdentityHeaders.SESSION_ID)));
            return true;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }
    }

    @Override
    public void afterCompletion(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        Exception exception
    ) {
        UserContext.clear();
    }

    @Override
    public void afterConcurrentHandlingStarted(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler
    ) {
        UserContext.clear();
    }
}
