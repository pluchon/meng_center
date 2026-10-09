package com.pluchon.meng.user.client;

import com.aliyun.dysmsapi20180501.Client;
import com.aliyun.dysmsapi20180501.models.SendMessageToGlobeRequest;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.user.config.UserProperties;
import com.pluchon.meng.user.mq.NotificationKind;
import com.pluchon.meng.user.mq.UserMqNames;
import com.pluchon.meng.user.service.error.UserErrorCode;
import java.text.MessageFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// 国际短信（台湾、港澳及其他境外号码）：阿里云 SendMessageToGlobe，发送方和正文来自配置
@Component
public final class InternationalSmsClient {

    private static final String OTP_TYPE = "OTP";
    private static final String NOTIFY_TYPE = "NOTIFY";
    // 归一化手机号开头的加号，接口要求不带
    private static final int PLUS_LENGTH = 1;

    // 客户端内部带连接池，建一次后复用
    private volatile Client client;

    @Autowired
    private UserProperties properties;

    // 通道没开通时明确失败；审核通知另有开关，关着就静默跳过
    public void send(String recipient, NotificationKind kind, String content) throws Exception {
        var sms = properties.internationalSms();
        if (!sms.enabled()) {
            throw new BusinessException(UserErrorCode.SMS_UNAVAILABLE);
        }
        if (kind != NotificationKind.CODE && !sms.approvalNotificationEnabled()) {
            return;
        }
        String text = switch (kind) {
            case CODE -> MessageFormat.format(sms.codeText(), content);
            case APPROVED -> sms.approvedText();
            case REJECTED -> MessageFormat.format(sms.rejectedText(), content);
        };
        var request = new SendMessageToGlobeRequest()
            .setTo(recipient.substring(PLUS_LENGTH))
            .setFrom(sms.senderId())
            .setMessage(text)
            .setType(kind == NotificationKind.CODE ? OTP_TYPE : NOTIFY_TYPE);
        String result = client(sms.endpoint()).sendMessageToGlobe(request).getBody().getResponseCode();
        if (!UserMqNames.PROVIDER_OK.equals(result)) {
            throw new BusinessException(UserErrorCode.DELIVERY_FAILED);
        }
    }

    private Client client(String endpoint) throws Exception {
        Client current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    current = new Client(AliyunSmsSupport.config(endpoint));
                    client = current;
                }
            }
        }
        return current;
    }
}
