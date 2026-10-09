package com.pluchon.meng.gateway;

import static org.junit.jupiter.api.Assertions.*;

import com.pluchon.meng.gateway.filter.PathPolicy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PathPolicyTest {

    @ParameterizedTest
    @ValueSource(
        strings = {
            "/user/internal/users",
            "/user/INTERNAL/users",
            "/user/%69nternal/users",
            "/user/%2569nternal/users",
            "/user//internal;anything/users",
            "/user%2Finternal/users",
        }
    )
    void internalVariantsRemainBlocked(String path) {
        assertTrue(PathPolicy.internal(PathPolicy.normalize(path)));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "/user/../internal",
            "/user/%2e%2e/internal",
            "/user/%00/internal",
            "/user/%5cinternal",
            "/user/%broken",
        }
    )
    void malformedOrAmbiguousPathsAreRejected(String path) {
        assertThrows(IllegalArgumentException.class, () -> PathPolicy.normalize(path));
    }
}
