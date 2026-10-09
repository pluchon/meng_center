package com.pluchon.meng.user.service.security;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.user.config.UserProperties;
import com.pluchon.meng.user.service.error.UserErrorCode;
import com.pluchon.meng.user.entity.enums.VerificationPurpose;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public final class VerificationCodeService {

    private final StringRedisTemplate redis;
    private final UserProperties properties;
    private final PiiCipher cipher;
    private final SecureRandom random = new SecureRandom();
    private final DefaultRedisScript<Long> verify;

    public VerificationCodeService(StringRedisTemplate redis, UserProperties properties, PiiCipher cipher) {
        this.redis = redis;
        this.properties = properties;
        this.cipher = cipher;
        verify = new DefaultRedisScript<>();
        verify.setLocation(new ClassPathResource("redis/verify-code.lua"));
        verify.setResultType(Long.class);
    }

    private String key(ContactService.Contact contact, VerificationPurpose purpose) {
        return (
            purpose.prefix() +
            (contact.channel() == com.pluchon.meng.user.entity.enums.ContactChannel.EMAIL
                ? "email-code:"
                : "sms-code:") +
            contact.hash()
        );
    }

    public String generate() {
        int digits = properties.verification().codeDigits();
        return String.format(Locale.ROOT, "%0" + digits + "d", random.nextInt((int) Math.pow(10, digits)));
    }

    public void store(ContactService.Contact contact, String code) {
        store(contact, code, VerificationPurpose.REGISTRATION);
    }

    public void store(ContactService.Contact contact, String code, VerificationPurpose purpose) {
        var script = new DefaultRedisScript<Long>(
            "redis.call('HSET',KEYS[1],'hash',ARGV[1],'errors','0');redis.call('EXPIRE',KEYS[1],ARGV[2]);return 1",
            Long.class
        );
        redis.execute(
            script,
            List.of(key(contact, purpose)),
            cipher.hash(code),
            Long.toString(properties.verification().codeTtl().toSeconds())
        );
    }

    public void consume(ContactService.Contact contact, String code) {
        consume(contact, code, VerificationPurpose.REGISTRATION);
    }

    public void consume(ContactService.Contact contact, String code, VerificationPurpose purpose) {
        Long result = redis.execute(
            verify,
            List.of(key(contact, purpose)),
            cipher.hash(code == null ? "" : code),
            Integer.toString(properties.verification().maxErrors())
        );
        if (result == null || result != 1) throw new BusinessException(UserErrorCode.INVALID_CODE);
    }
}
