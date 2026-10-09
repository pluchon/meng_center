package com.pluchon.meng.common.web.interceptor;

import static org.junit.jupiter.api.Assertions.*;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.security.IdentityHeaders;
import com.pluchon.meng.common.web.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class IdentityInterceptorTest {

    private final IdentityInterceptor interceptor = new IdentityInterceptor();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void cleanup() {
        UserContext.clear();
    }

    @Test
    void identityIsClearedAfterNormalAndAsyncRequests() {
        var request = new MockHttpServletRequest();
        request.addHeader(IdentityHeaders.USER_ID, "42");
        request.addHeader(IdentityHeaders.USER_ROLE, "ADMIN");
        assertTrue(interceptor.preHandle(request, response, this));
        assertEquals(42, UserContext.require().userId());
        interceptor.afterCompletion(request, response, this, null);
        assertTrue(UserContext.current().isEmpty());
        interceptor.preHandle(request, response, this);
        interceptor.afterConcurrentHandlingStarted(request, response, this);
        assertTrue(UserContext.current().isEmpty());
    }

    @Test
    void nextAnonymousRequestCannotInheritPreviousIdentity() {
        var request = new MockHttpServletRequest();
        request.addHeader(IdentityHeaders.USER_ID, "42");
        request.addHeader(IdentityHeaders.USER_ROLE, "ADMIN");
        interceptor.preHandle(request, response, this);
        interceptor.preHandle(new MockHttpServletRequest(), response, this);
        assertTrue(UserContext.current().isEmpty());
    }

    @Test
    void incompleteOrMalformedIdentityIsRejected() {
        var request = new MockHttpServletRequest();
        request.addHeader(IdentityHeaders.USER_ID, "invalid");
        assertThrows(BusinessException.class, () -> interceptor.preHandle(request, response, this));
        assertTrue(UserContext.current().isEmpty());
    }
}
