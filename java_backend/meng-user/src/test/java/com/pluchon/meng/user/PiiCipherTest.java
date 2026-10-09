package com.pluchon.meng.user;

import static org.junit.jupiter.api.Assertions.*;

import com.pluchon.meng.user.service.security.PiiCipher;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class PiiCipherTest {

    private static String key(int fill) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) fill);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void encryptionUsesFreshNonceAndStableIndependentLookupHash() {
        var cipher = new PiiCipher(key(1), key(2));
        String first = cipher.encrypt("hello@example.com"),
            second = cipher.encrypt("hello@example.com");
        assertNotEquals(first, second);
        assertEquals("hello@example.com", cipher.decrypt(first));
        assertEquals(cipher.hash("hello@example.com"), cipher.hash("hello@example.com"));
        assertNotEquals(cipher.hash("hello@example.com"), new PiiCipher(key(1), key(3)).hash("hello@example.com"));
        assertFalse(first.contains("hello"));
    }

    @Test
    void keysCannotBeReusedAndTamperingIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PiiCipher(key(1), key(1)));
        var cipher = new PiiCipher(key(1), key(2));
        String encrypted = cipher.encrypt("private");
        assertThrows(RuntimeException.class, () -> new PiiCipher(key(3), key(2)).decrypt(encrypted));
        assertThrows(RuntimeException.class, () -> cipher.decrypt("v2:unsupported"));
    }
}
