package com.pluchon.meng.user.service;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.user.config.UserProperties;
import com.pluchon.meng.user.controller.vo.CaptchaTicketVO;
import com.pluchon.meng.user.service.error.UserErrorCode;
import com.pluchon.meng.user.service.security.PiiCipher;
import com.pluchon.meng.user.service.security.RateLimitService;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

// 滑块验证：出题、校验、签发一次性票据，敏感操作再凭票据放行
@Service
public final class CaptchaService {

    private static final String TICKET_PREFIX = "meng:captcha-ticket:";

    @Autowired
    private ImageCaptchaApplication captcha;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private UserProperties properties;

    @Autowired
    private PiiCipher cipher;

    @Autowired
    private RateLimitService limits;

    // 生成一道滑块题，出题要画图，按 IP 限频防止被刷
    public ImageCaptchaVO generate(String ip) {
        limits.charge("captcha-ip", ip, properties.limits().captchaIpMinuteMax(), Duration.ofMinutes(1));
        var result = captcha.generateCaptcha(CaptchaTypeConstant.SLIDER);
        if (!result.isSuccess()) {
            throw new BusinessException(UserErrorCode.INVALID_TICKET);
        }
        return result.getData();
    }

    // 位置和轨迹都通过后签发票据，票据绑定来源地址
    public CaptchaTicketVO verify(String id, ImageCaptchaTrack track, String ip) {
        if (!matches(id, track)) {
            throw new BusinessException(UserErrorCode.INVALID_TICKET);
        }
        String ticket = UUID.randomUUID().toString();
        redis.opsForValue().set(TICKET_PREFIX + ticket, cipher.hash(ip), properties.verification().ticketTtl());
        return new CaptchaTicketVO(ticket);
    }

    // 票据取出即删，只能用一次，且必须来自签发时的地址
    public void consume(String ticket, String ip) {
        if (ticket == null || ticket.isBlank()) {
            throw new BusinessException(UserErrorCode.INVALID_TICKET);
        }
        String expected = redis.opsForValue().getAndDelete(TICKET_PREFIX + ticket);
        if (!cipher.hash(ip).equals(expected)) {
            throw new BusinessException(UserErrorCode.INVALID_TICKET);
        }
    }

    // 轨迹字段不完整时轨迹校验器会抛参数异常，统一按验证未通过处理
    private boolean matches(String id, ImageCaptchaTrack track) {
        if (track == null) {
            return false;
        }
        try {
            return captcha.matching(id, track).isSuccess();
        } catch (IllegalArgumentException incomplete) {
            return false;
        }
    }
}
