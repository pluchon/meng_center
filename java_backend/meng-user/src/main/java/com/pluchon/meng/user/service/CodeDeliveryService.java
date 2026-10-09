package com.pluchon.meng.user.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.user.config.UserProperties;
import com.pluchon.meng.user.entity.enums.ContactChannel;
import com.pluchon.meng.user.entity.enums.VerificationPurpose;
import com.pluchon.meng.user.mq.*;
import com.pluchon.meng.user.service.error.UserErrorCode;
import com.pluchon.meng.user.service.security.*;
import java.time.Clock;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public final class CodeDeliveryService {

    private final CaptchaService captcha;
    private final ContactService contacts;
    private final RateLimitService limits;
    private final VerificationCodeService codes;
    private final RabbitTemplate rabbit;
    private final PiiCipher cipher;
    private final Clock clock;
    private final UserProperties properties;

    public CodeDeliveryService(
        CaptchaService captcha,
        ContactService contacts,
        RateLimitService limits,
        VerificationCodeService codes,
        RabbitTemplate rabbit,
        PiiCipher cipher,
        Clock clock,
        UserProperties properties
    ) {
        this.captcha = captcha;
        this.contacts = contacts;
        this.limits = limits;
        this.codes = codes;
        this.rabbit = rabbit;
        this.cipher = cipher;
        this.clock = clock;
        this.properties = properties;
        rabbit.setReplyTimeout(
            properties.notification().rpcTimeout().toMillis()
        );
        rabbit.setMandatory(true);
    }

    public void email(String email, String ticket, String ip) {
        send(
            contacts.normalize(email, null, null),
            ticket,
            ip,
            VerificationPurpose.REGISTRATION
        );
    }

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

    private void send(
        ContactService.Contact contact,
        String ticket,
        String ip,
        VerificationPurpose purpose
    ) {
        captcha.consume(ticket, ip);
        limits.send(contact, ip);
        if (contact.channel() == ContactChannel.SMS) {
            String recipient = cipher.decrypt(contact.cipher());
            boolean enabled =
                com.pluchon.meng.user.entity.enums.PhoneRegion.DOMESTIC.equals(
                    contacts.countryCode(recipient)
                )
                    ? properties.sms().enabled()
                    : properties.internationalSms().enabled();
            if (!enabled) throw new BusinessException(
                UserErrorCode.SMS_UNAVAILABLE
            );
        }
        String code = codes.generate();
        var command = new DeliveryCommand(
            UUID.randomUUID().toString(),
            contact.channel(),
            contact.cipher(),
            NotificationKind.CODE,
            cipher.encrypt(code),
            clock.instant().plus(properties.verification().codeTtl())
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
        if (!UserMqNames.DELIVERY_OK.equals(reply)) throw new BusinessException(
            UserErrorCode.DELIVERY_FAILED
        );
        codes.store(contact, code, purpose);
    }
}
