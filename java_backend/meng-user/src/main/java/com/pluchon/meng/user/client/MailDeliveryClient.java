package com.pluchon.meng.user.client;

import com.pluchon.meng.user.config.UserProperties;
import com.pluchon.meng.user.mq.NotificationKind;
import java.text.MessageFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

// 邮件发送：经 SMTP 发纯文本，主题和正文模板来自配置
@Component
public final class MailDeliveryClient {

    @Autowired
    private JavaMailSender sender;

    @Autowired
    private UserProperties properties;

    // 验证码和拒绝原因填进正文模板，通过通知是固定文案
    public void send(String recipient, NotificationKind kind, String content) {
        var configured = properties.notification();
        var message = new SimpleMailMessage();
        message.setFrom(configured.sender());
        message.setTo(recipient);
        message.setSubject(
            switch (kind) {
                case CODE -> configured.codeSubject();
                case APPROVED -> configured.approvedSubject();
                case REJECTED -> configured.rejectedSubject();
            }
        );
        message.setText(
            switch (kind) {
                case CODE -> MessageFormat.format(configured.codeText(), content);
                case APPROVED -> configured.approvedText();
                case REJECTED -> MessageFormat.format(configured.rejectedText(), content);
            }
        );
        sender.send(message);
    }
}
