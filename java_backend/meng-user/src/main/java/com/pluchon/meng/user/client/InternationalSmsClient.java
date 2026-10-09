package com.pluchon.meng.user.client;

import com.aliyun.dysmsapi20180501.models.SendMessageToGlobeRequest;
import com.aliyun.teaopenapi.models.Config;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.user.config.UserProperties;
import com.pluchon.meng.user.mq.*;
import com.pluchon.meng.user.service.error.UserErrorCode;
import java.text.MessageFormat;
import org.springframework.stereotype.Component;

@Component
public final class InternationalSmsClient {

    private static final String OTP_TYPE = "OTP";
    private static final String NOTIFY_TYPE = "NOTIFY";
    private final UserProperties properties;

    public InternationalSmsClient(UserProperties properties) {
        this.properties = properties;
    }

    public void send(String recipient, NotificationKind kind, String content) throws Exception {
        var sms = properties.internationalSms();
        if (!sms.enabled()) throw new BusinessException(UserErrorCode.SMS_UNAVAILABLE);
        if (kind != NotificationKind.CODE && !sms.approvalNotificationEnabled()) return;
        String text = switch (kind) {
            case CODE -> MessageFormat.format(sms.codeText(), content);
            case APPROVED -> sms.approvedText();
            case REJECTED -> MessageFormat.format(sms.rejectedText(), content);
        };
        var client = new com.aliyun.dysmsapi20180501.Client(
            new Config()
                .setAccessKeyId(System.getenv("ALIYUN_ACCESS_KEY_ID"))
                .setAccessKeySecret(System.getenv("ALIYUN_ACCESS_KEY_SECRET"))
                .setEndpoint(sms.endpoint())
                .setReadTimeout(5000)
                .setConnectTimeout(5000)
        );
        var request = new SendMessageToGlobeRequest()
            .setTo(recipient.substring(1))
            .setFrom(sms.senderId())
            .setMessage(text)
            .setType(kind == NotificationKind.CODE ? OTP_TYPE : NOTIFY_TYPE);
        if (
            !UserMqNames.PROVIDER_OK.equals(client.sendMessageToGlobe(request).getBody().getResponseCode())
        ) throw new BusinessException(UserErrorCode.DELIVERY_FAILED);
    }
}
