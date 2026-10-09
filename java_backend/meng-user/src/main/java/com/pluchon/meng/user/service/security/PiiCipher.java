package com.pluchon.meng.user.service.security;

import com.pluchon.meng.common.core.security.AuthConstants;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public final class PiiCipher {

    private static final String VERSION_PREFIX = "v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private final byte[] encryptionKey;
    private final byte[] hashKey;
    private final SecureRandom random = new SecureRandom();

    public PiiCipher() {
        this(System.getenv(AuthConstants.PII_KEY_ENV), System.getenv(AuthConstants.PII_HASH_KEY_ENV));
    }

    public PiiCipher(String encryptionKey, String hashKey) {
        this.encryptionKey = decodeKey(encryptionKey);
        this.hashKey = decodeKey(hashKey);
        if (java.security.MessageDigest.isEqual(this.encryptionKey, this.hashKey)) {
            throw new IllegalArgumentException("PII encryption and hashing keys must differ");
        }
    }

    private static byte[] decodeKey(String input) {
        try {
            byte[] key = Base64.getDecoder().decode(input);
            if (key.length != 32) throw new IllegalArgumentException();
            return key;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("PII key must be Base64 encoded 32 bytes");
        }
    }

    public String encrypt(String value) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                Cipher.ENCRYPT_MODE,
                new SecretKeySpec(encryptionKey, "AES"),
                new GCMParameterSpec(TAG_BITS, iv)
            );
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return (
                VERSION_PREFIX +
                Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ciphertext.length)
                        .put(iv)
                        .put(ciphertext)
                        .array()
                )
            );
        } catch (Exception exception) {
            throw new IllegalStateException("PII encryption failed");
        }
    }

    public String decrypt(String value) {
        try {
            if (!value.startsWith(VERSION_PREFIX)) throw new IllegalArgumentException();
            byte[] packed = Base64.getDecoder().decode(value.substring(VERSION_PREFIX.length()));
            if (packed.length < IV_LENGTH + TAG_BITS / 8) throw new IllegalArgumentException();
            ByteBuffer buffer = ByteBuffer.wrap(packed);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                Cipher.DECRYPT_MODE,
                new SecretKeySpec(encryptionKey, "AES"),
                new GCMParameterSpec(TAG_BITS, iv)
            );
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("PII decryption failed");
        }
    }

    public String hash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("PII hashing failed");
        }
    }
}
