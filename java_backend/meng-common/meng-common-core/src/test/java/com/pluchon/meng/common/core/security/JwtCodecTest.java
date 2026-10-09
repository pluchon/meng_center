package com.pluchon.meng.common.core.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtCodecTest {

    private static String key(int fill) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, (byte) fill);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void signedTokenContainsOnlySessionLookupKey() {
        var codec = new JwtCodec(key(1));
        var session = new SessionKey(42, "test-session");
        String token = codec.encode(session);
        assertEquals(session, codec.decode(token));
        String payload = new String(
            Base64.getUrlDecoder().decode(token.split("\\.")[1]),
            java.nio.charset.StandardCharsets.UTF_8
        );
        assertFalse(payload.contains("exp"));
        assertFalse(payload.contains("role"));
        assertFalse(payload.contains("password"));
    }

    @Test
    void wrongSigningKeyAndUnsignedTokenAreRejected() {
        var codec = new JwtCodec(key(1));
        String token = codec.encode(new SessionKey(42, "test-session"));
        assertThrows(RuntimeException.class, () -> new JwtCodec(key(2)).decode(token));
        String unsigned = "eyJhbGciOiJub25lIn0.eyJ1c2VySWQiOjQyLCJzZXNzaW9uSWQiOiJ0ZXN0In0.";
        assertThrows(RuntimeException.class, () -> codec.decode(unsigned));
    }

    @Test
    void weakSigningKeyAndInvalidIdentityAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            new JwtCodec(Base64.getEncoder().encodeToString(new byte[16]))
        );
        assertThrows(IllegalArgumentException.class, () -> new SessionKey(0, "session"));
        assertThrows(IllegalArgumentException.class, () -> new SessionKey(1, ""));
    }
}
