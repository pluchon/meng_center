package com.pluchon.meng.user.service;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
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
        var result = captcha.generateCaptcha(CaptchaTypeConstant.SLIDER);
        if (!result.isSuccess()) throw new BusinessException(UserErrorCode.INVALID_TICKET);
        return result.getData();
    }

    public Ticket verify(String id, ImageCaptchaTrack track, String ip) {
        if (!matches(id, track)) throw new BusinessException(UserErrorCode.INVALID_TICKET);
        String ticket = UUID.randomUUID().toString();
        redis.opsForValue().set(TICKET_PREFIX + ticket, cipher.hash(ip), properties.verification().ticketTtl());
        return new Ticket(ticket);
    }

    public void consume(String ticket, String ip) {
        if (ticket == null || ticket.isBlank()) throw new BusinessException(UserErrorCode.INVALID_TICKET);
        String expected = redis.opsForValue().getAndDelete(TICKET_PREFIX + ticket);
        if (!cipher.hash(ip).equals(expected)) throw new BusinessException(UserErrorCode.INVALID_TICKET);
    }

    // 轨迹字段不完整时轨迹校验器会抛参数异常，统一按验证未通过处理
    private boolean matches(String id, ImageCaptchaTrack track) {
        if (track == null) return false;
        try {
            return captcha.matching(id, track).isSuccess();
        } catch (IllegalArgumentException incomplete) {
            return false;
        }
    }

    public record Ticket(String ticket) {}
}
