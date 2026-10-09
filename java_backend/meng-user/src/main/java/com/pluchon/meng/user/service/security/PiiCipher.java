package com.pluchon.meng.user.service.security;

import com.pluchon.meng.common.core.security.AuthConstants;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

// 邮箱和手机号的加密与检索哈希：AES-256-GCM 存密文，HMAC-SHA256 做等值查找
@Component
public final class PiiCipher {

    // 密文版本前缀，换算法或换密钥时用来区分旧数据
    private static final String VERSION_PREFIX = "v1:";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final String HASH_ALGORITHM = "HmacSHA256";
    private static final int KEY_LENGTH = 32;
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey encryptionKey;
    private final SecretKey hashKey;
    private final SecureRandom random = new SecureRandom();

    public PiiCipher() {
        this(System.getenv(AuthConstants.PII_KEY_ENV), System.getenv(AuthConstants.PII_HASH_KEY_ENV));
    }

    public PiiCipher(String encryptionKey, String hashKey) {
        byte[] encryption = decodeKey(encryptionKey);
        byte[] hash = decodeKey(hashKey);
        if (MessageDigest.isEqual(encryption, hash)) {
            throw new IllegalArgumentException("PII encryption and hashing keys must differ");
        }
        this.encryptionKey = new SecretKeySpec(encryption, "AES");
        this.hashKey = new SecretKeySpec(hash, HASH_ALGORITHM);
    }

    private static byte[] decodeKey(String input) {
        try {
            byte[] key = Base64.getDecoder().decode(input);
            if (key.length != KEY_LENGTH) {
                throw new IllegalArgumentException();
            }
            return key;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("PII key must be Base64 encoded 32 bytes");
        }
    }

    // 每次加密用新的随机 IV，同一明文的密文各不相同
    public String encrypt(String value) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] packed = ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(packed);
        } catch (Exception exception) {
            throw new IllegalStateException("PII encryption failed");
        }
    }

    // 版本不符、被篡改或密钥不对都会失败，不返回任何明文片段
    public String decrypt(String value) {
        try {
            if (!value.startsWith(VERSION_PREFIX)) {
                throw new IllegalArgumentException();
            }
            byte[] packed = Base64.getDecoder().decode(value.substring(VERSION_PREFIX.length()));
            if (packed.length < IV_LENGTH + TAG_BITS / 8) {
                throw new IllegalArgumentException();
            }
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, packed, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(packed, IV_LENGTH, packed.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("PII decryption failed");
        }
    }

    // 带密钥的确定性哈希，用于唯一索引、限流键和票据绑定
    public String hash(String value) {
        try {
            Mac mac = Mac.getInstance(HASH_ALGORITHM);
            mac.init(hashKey);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("PII hashing failed");
        }
    }
}
