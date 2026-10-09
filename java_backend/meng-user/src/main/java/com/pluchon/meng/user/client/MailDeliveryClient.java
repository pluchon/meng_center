package com.pluchon.meng.user.client;

import com.pluchon.meng.user.config.UserProperties;
import com.pluchon.meng.user.mq.NotificationKind;
import java.text.MessageFormat;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public final class MailDeliveryClient {

    private final JavaMailSender sender;
    private final UserProperties properties;

    public MailDeliveryClient(JavaMailSender sender, UserProperties properties) {
        this.sender = sender;
        this.properties = properties;
    }

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
