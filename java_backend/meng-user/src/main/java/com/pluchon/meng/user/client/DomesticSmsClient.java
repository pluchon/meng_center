package com.pluchon.meng.user.client;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.user.config.UserProperties;
import com.pluchon.meng.user.mq.NotificationKind;
import com.pluchon.meng.user.mq.UserMqNames;
import com.pluchon.meng.user.service.error.UserErrorCode;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// 国内短信：阿里云 SendSms，签名和模板编号来自配置
@Component
public final class DomesticSmsClient {

    // 归一化手机号里「+86」的长度，接口只要国内号码本身
    private static final int COUNTRY_CODE_LENGTH = 3;
    private static final String CODE_PARAMETER = "code";
    private static final String REASON_PARAMETER = "reason";

    // 客户端内部带连接池，建一次后复用
    private volatile Client client;

    @Autowired
    private UserProperties properties;

    // 通道没开通时明确失败；审核通知另有开关，关着就静默跳过
    public void send(String recipient, NotificationKind kind, String content) throws Exception {
        var sms = properties.sms();
        if (!sms.enabled()) {
            throw new BusinessException(UserErrorCode.SMS_UNAVAILABLE);
        }
        if (kind != NotificationKind.CODE && !sms.approvalNotificationEnabled()) {
            return;
        }
        String template = switch (kind) {
            case CODE -> sms.domesticCodeTemplate();
            case APPROVED -> sms.approvedTemplate();
            case REJECTED -> sms.rejectedTemplate();
        };
        var request = new SendSmsRequest()
            .setPhoneNumbers(recipient.substring(COUNTRY_CODE_LENGTH))
            .setSignName(sms.signName())
            .setTemplateCode(template);
        if (kind == NotificationKind.CODE) {
            request.setTemplateParam(AliyunSmsSupport.GSON.toJson(Map.of(CODE_PARAMETER, content)));
        }
        if (kind == NotificationKind.REJECTED) {
            request.setTemplateParam(AliyunSmsSupport.GSON.toJson(Map.of(REASON_PARAMETER, content)));
        }
        String result = client(sms.endpoint()).sendSms(request).getBody().getCode();
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
