package com.pluchon.meng.user.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.web.context.UserContext;
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
        var contact = contacts.normalize(email, countryCode, phone);
        if (contact.channel() == ContactChannel.SMS && !smsEnabled(contact)) {
            throw new BusinessException(UserErrorCode.SMS_UNAVAILABLE);
        }
        captcha.consume(ticket, ip);
        // 换绑发码在联系方式的限制之外，再按账号限制每天次数
        limits.chargeDaily(
            "contact-code-account",
            String.valueOf(UserContext.require().userId()),
            properties.limits().contactCodeDailyMax()
        );
        limits.send(contact, ip);
        deliver(contact, ip, VerificationPurpose.CONTACT_CHANGE);
    }

    // 登录后忘记原密码：验证码发到账号绑定的手机号。短信通道没开通、但开了模拟时不真的发，
    // 把码返回给页面显示；真的发了短信就返回空
    public String myResetCode(ContactService.Contact contact, String ticket, String ip) {
        boolean enabled = smsEnabled(contact);
        // 通道没开通又不模拟时直接拒绝，不消耗滑块票据和发码额度
        if (!enabled && !properties.sms().mock()) {
            throw new BusinessException(UserErrorCode.SMS_UNAVAILABLE);
        }
        captcha.consume(ticket, ip);
        limits.send(contact, ip);
        if (enabled) {
            deliver(contact, ip, VerificationPurpose.PASSWORD_RESET);
            return null;
        }
        String code = codes.generate();
        codes.store(contact, code, VerificationPurpose.PASSWORD_RESET);
        return code;
    }

    // 票据和限流通过后才发送
    private void send(
        ContactService.Contact contact,
        String ticket,
        String ip,
        VerificationPurpose purpose
    ) {
        // 通道没开通时直接拒绝，不消耗滑块票据和发码额度
        if (contact.channel() == ContactChannel.SMS && !smsEnabled(contact)) {
            throw new BusinessException(UserErrorCode.SMS_UNAVAILABLE);
        }
        captcha.consume(ticket, ip);
        limits.send(contact, ip);
        deliver(contact, ip, purpose);
    }

    // 收到成功回执才保存验证码，用户不会拿到一个用不了的码；没发出去就退回发码额度
    private void deliver(ContactService.Contact contact, String ip, VerificationPurpose purpose) {
        try {
            dispatch(contact, purpose);
        } catch (BusinessException failure) {
            try {
                limits.refundSend(contact, ip);
            } catch (RuntimeException refundFailure) {
                failure.addSuppressed(refundFailure);
            }
            throw failure;
        }
    }

    private void dispatch(ContactService.Contact contact, VerificationPurpose purpose) {
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
        // 先存码再发：邮件发得慢、请求方等回执超时之后才送达时，用户收到的码仍然有效
        codes.store(contact, code, purpose);
        Object reply;
        try {
            reply = rabbit.convertSendAndReceive(
                UserMqNames.INTERNAL_EXCHANGE,
                UserMqNames.CODE_ROUTE,
                command
            );
        } catch (RuntimeException exception) {
            codes.discard(contact, code, purpose);
            throw new BusinessException(UserErrorCode.DELIVERY_FAILED);
        }
        // 消费方明确回复失败才作废；等回执超时（reply 为空）时码可能已发出，留到自然过期
        if (UserMqNames.DELIVERY_FAILED.equals(reply)) codes.discard(contact, code, purpose);
        if (!UserMqNames.DELIVERY_OK.equals(reply)) {
            throw new BusinessException(UserErrorCode.DELIVERY_FAILED);
        }
    }

    // 国内和国际短信是两条通道，看手机号属于哪一条、那条开没开通
    private boolean smsEnabled(ContactService.Contact contact) {
        String region = contacts.countryCode(cipher.decrypt(contact.cipher()));
        return PhoneRegion.DOMESTIC.equals(region)
            ? properties.sms().enabled()
            : properties.internationalSms().enabled();
    }
}
