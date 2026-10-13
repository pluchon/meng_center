package com.pluchon.meng.user.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.user.config.UserProperties;
import com.pluchon.meng.user.entity.enums.ContactChannel;
import com.pluchon.meng.user.entity.enums.PhoneRegion;
import com.pluchon.meng.user.entity.enums.VerificationPurpose;
import com.pluchon.meng.user.mq.DeliveryCommand;
import com.pluchon.meng.user.mq.NotificationKind;
import com.pluchon.meng.user.mq.UserMqNames;
import com.pluchon.meng.user.service.error.UserErrorCode;
import com.pluchon.meng.user.service.security.ContactService;
import com.pluchon.meng.user.service.security.PiiCipher;
import com.pluchon.meng.user.service.security.RateLimitService;
import com.pluchon.meng.user.service.security.VerificationCodeService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// 发验证码：发送交给消息队列里的消费者执行，请求线程等回执，发不出去就让接口失败
@Service
public final class CodeDeliveryService {

    @Autowired
    private CaptchaService captcha;

    @Autowired
    private ContactService contacts;

    @Autowired
    private RateLimitService limits;

    @Autowired
    private VerificationCodeService codes;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private PiiCipher cipher;

    @Autowired
    private Clock clock;

    @Autowired
    private UserProperties properties;

    // 注册用的邮箱验证码
    public void email(String email, String ticket, String ip) {
        send(
            contacts.normalize(email, null, null),
            ticket,
            ip,
            VerificationPurpose.REGISTRATION
        );
    }

    // 注册用的短信验证码
    public void sms(
        String countryCode,
        String phone,
        String ticket,
        String ip
    ) {
        send(
            contacts.normalize(null, countryCode, phone),
            ticket,
            ip,
            VerificationPurpose.REGISTRATION
        );
    }

    // 重置密码用的验证码，邮箱和手机号二选一
    public void resetCode(
        String email,
        String countryCode,
        String phone,
        String ticket,
        String ip
    ) {
        send(
            contacts.normalize(email, countryCode, phone),
            ticket,
            ip,
            VerificationPurpose.PASSWORD_RESET
        );
    }

    public void contactCode(
        String email,
        String countryCode,
        String phone,
        String ticket,
        String ip
    ) {
        send(
            contacts.normalize(email, countryCode, phone),
            ticket,
            ip,
            VerificationPurpose.CONTACT_CHANGE
        );
    }

    // 票据和限流通过后才发送；收到成功回执才保存验证码，用户不会拿到一个用不了的码
    private void send(
        ContactService.Contact contact,
        String ticket,
        String ip,
        VerificationPurpose purpose
    ) {
        captcha.consume(ticket, ip);
        limits.send(contact, ip);
        if (contact.channel() == ContactChannel.SMS) {
            requireSmsChannel(contact);
        }
        String code = codes.generate();
        // 过了等回执的时限请求方已经放弃，消费者此后再拿到这条指令就不该发了
        var command = new DeliveryCommand(
            UUID.randomUUID().toString(),
            contact.channel(),
            contact.cipher(),
            NotificationKind.CODE,
            cipher.encrypt(code),
            clock.instant().plus(properties.notification().rpcTimeout())
        );
        Object reply;
        try {
            reply = rabbit.convertSendAndReceive(
                UserMqNames.INTERNAL_EXCHANGE,
                UserMqNames.CODE_ROUTE,
                command
            );
        } catch (RuntimeException exception) {
            throw new BusinessException(UserErrorCode.DELIVERY_FAILED);
        }
        if (!UserMqNames.DELIVERY_OK.equals(reply)) {
            throw new BusinessException(UserErrorCode.DELIVERY_FAILED);
        }
        codes.store(contact, code, purpose);
    }

    // 国内和国际短信是两条通道，对应的那条没开通就明确告诉用户
    private void requireSmsChannel(ContactService.Contact contact) {
        String region = contacts.countryCode(cipher.decrypt(contact.cipher()));
        boolean enabled = PhoneRegion.DOMESTIC.equals(region)
            ? properties.sms().enabled()
            : properties.internationalSms().enabled();
        if (!enabled) {
            throw new BusinessException(UserErrorCode.SMS_UNAVAILABLE);
        }
    }
}
