package com.pluchon.meng.user.service.security;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.user.config.UserProperties;
import com.pluchon.meng.user.entity.enums.ContactChannel;
import com.pluchon.meng.user.entity.enums.VerificationPurpose;
import com.pluchon.meng.user.service.error.UserErrorCode;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

// 邮箱和短信验证码：只存哈希，按用途分键，用对即删，错够次数作废
@Service
public final class VerificationCodeService {

    private static final String EMAIL_SEGMENT = "email-code:";
    private static final String SMS_SEGMENT = "sms-code:";
    private static final long SUCCESS = 1L;

    private final SecureRandom random = new SecureRandom();
    private final DefaultRedisScript<Long> store = RedisScripts.counting("redis/store-code.lua");
    private final DefaultRedisScript<Long> verify = RedisScripts.counting("redis/verify-code.lua");

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private UserProperties properties;

    @Autowired
    private PiiCipher cipher;

    // 注册码和重置码用不同前缀，互相不能顶替
    private String key(ContactService.Contact contact, VerificationPurpose purpose) {
        String segment = contact.channel() == ContactChannel.EMAIL ? EMAIL_SEGMENT : SMS_SEGMENT;
        return purpose.prefix() + segment + contact.hash();
    }

    // 生成定长数字验证码，不足位数补零
    public String generate() {
        int digits = properties.verification().codeDigits();
        return String.format(Locale.ROOT, "%0" + digits + "d", random.nextInt((int) Math.pow(10, digits)));
    }

    // 保存验证码哈希并重置错误次数，新码覆盖旧码
    public void store(ContactService.Contact contact, String code, VerificationPurpose purpose) {
        redis.execute(
            store,
            List.of(key(contact, purpose)),
            cipher.hash(code),
            Long.toString(properties.verification().codeTtl().toSeconds())
        );
    }

    // 校验并消耗验证码，不正确或已失效时抛出业务异常
    public void consume(ContactService.Contact contact, String code, VerificationPurpose purpose) {
        Long result = redis.execute(
            verify,
            List.of(key(contact, purpose)),
            cipher.hash(code == null ? "" : code),
            Integer.toString(properties.verification().maxErrors())
        );
        if (result == null || result != SUCCESS) {
            throw new BusinessException(UserErrorCode.INVALID_CODE);
        }
    }
}
