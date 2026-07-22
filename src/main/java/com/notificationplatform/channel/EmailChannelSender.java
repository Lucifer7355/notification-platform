package com.notificationplatform.channel;

import com.notificationplatform.domain.ChannelType;
import com.notificationplatform.domain.Notification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailChannelSender implements ChannelSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailChannelSender(
            JavaMailSender mailSender,
            @Value("${platform.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public ChannelType channel() {
        return ChannelType.EMAIL;
    }

    @Override
    public ChannelSendResult send(Notification notification) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(notification.recipient());
            message.setSubject(notification.subject());
            message.setText(notification.renderedBody());
            mailSender.send(message);
            return ChannelSendResult.success("smtp-" + notification.id());
        } catch (Exception ex) {
            return ChannelSendResult.failure("EMAIL send failed: " + ex.getMessage());
        }
    }
}
