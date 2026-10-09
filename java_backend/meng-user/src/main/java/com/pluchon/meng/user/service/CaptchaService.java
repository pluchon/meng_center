package com.pluchon.meng.user.service;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.user.config.UserProperties;
import com.pluchon.meng.user.service.error.UserErrorCode;
import com.pluchon.meng.user.service.security.PiiCipher;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public final class CaptchaService {

    private static final String TICKET_PREFIX = "meng:captcha-ticket:";
    private final ImageCaptchaApplication captcha;
    private final StringRedisTemplate redis;
    private final UserProperties properties;
    private final PiiCipher cipher;

    public CaptchaService(
        ImageCaptchaApplication captcha,
        StringRedisTemplate redis,
        UserProperties properties,
        PiiCipher cipher
    ) {
        this.captcha = captcha;
        this.redis = redis;
        this.properties = properties;
        this.cipher = cipher;
    }

    public ImageCaptchaVO generate() {
        var result = captcha.generateCaptcha();
        if (!result.isSuccess()) throw new BusinessException(UserErrorCode.INVALID_TICKET);
        return result.getData();
    }

    public Ticket verify(String id, ImageCaptchaTrack track, String ip) {
        if (track == null || !captcha.matching(id, track).isSuccess()) throw new BusinessException(
            UserErrorCode.INVALID_TICKET
        );
        String ticket = UUID.randomUUID().toString();
        redis.opsForValue().set(TICKET_PREFIX + ticket, cipher.hash(ip), properties.verification().ticketTtl());
        return new Ticket(ticket);
    }

    public void consume(String ticket, String ip) {
        if (ticket == null || ticket.isBlank()) throw new BusinessException(UserErrorCode.INVALID_TICKET);
        String expected = redis.opsForValue().getAndDelete(TICKET_PREFIX + ticket);
        if (!cipher.hash(ip).equals(expected)) throw new BusinessException(UserErrorCode.INVALID_TICKET);
    }

    public record Ticket(String ticket) {}
}
