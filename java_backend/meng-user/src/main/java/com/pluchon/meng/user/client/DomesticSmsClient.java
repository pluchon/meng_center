package com.pluchon.meng.user.client;

import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.teaopenapi.models.Config;
import com.google.gson.Gson;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.user.config.UserProperties;
import com.pluchon.meng.user.mq.*;
import com.pluchon.meng.user.service.error.UserErrorCode;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class DomesticSmsClient {

    private final UserProperties properties;

    public DomesticSmsClient(UserProperties properties) {
        this.properties = properties;
    }

    public void send(String recipient, NotificationKind kind, String content) throws Exception {
        var sms = properties.sms();
        if (!sms.enabled()) throw new BusinessException(UserErrorCode.SMS_UNAVAILABLE);
        if (kind != NotificationKind.CODE && !sms.approvalNotificationEnabled()) return;
        String template = switch (kind) {
            case CODE -> sms.domesticCodeTemplate();
            case APPROVED -> sms.approvedTemplate();
            case REJECTED -> sms.rejectedTemplate();
        };
        var client = new com.aliyun.dysmsapi20170525.Client(
            new Config()
                .setAccessKeyId(System.getenv("ALIYUN_ACCESS_KEY_ID"))
                .setAccessKeySecret(System.getenv("ALIYUN_ACCESS_KEY_SECRET"))
                .setEndpoint(sms.endpoint())
                .setReadTimeout(5000)
                .setConnectTimeout(5000)
        );
        var request = new SendSmsRequest()
            .setPhoneNumbers(recipient.substring(3))
            .setSignName(sms.signName())
            .setTemplateCode(template);
        if (kind == NotificationKind.CODE) request.setTemplateParam(new Gson().toJson(Map.of("code", content)));
        if (kind == NotificationKind.REJECTED) request.setTemplateParam(new Gson().toJson(Map.of("reason", content)));
        if (!UserMqNames.PROVIDER_OK.equals(client.sendSms(request).getBody().getCode())) throw new BusinessException(
            UserErrorCode.DELIVERY_FAILED
        );
    }
}
