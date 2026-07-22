package com.notificationplatform.validation;

import com.notificationplatform.domain.ChannelType;
import com.notificationplatform.dto.SendNotificationRequest;
import com.notificationplatform.exception.ValidationException;

import java.util.Objects;
import java.util.regex.Pattern;

public final class NotificationRequestValidator {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern E164_PHONE = Pattern.compile("^\\+[1-9]\\d{7,14}$");
    private static final Pattern SLACK_CHANNEL = Pattern.compile("^[#@][A-Za-z0-9_-]+$");
    private static final Pattern DEVICE_TOKEN = Pattern.compile("^[A-Za-z0-9:_-]{8,}$");

    public void validate(SendNotificationRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.recipient() == null || request.recipient().isBlank()) {
            throw new ValidationException("recipient is required");
        }
        if (request.templateId() == null || request.templateId().isBlank()) {
            throw new ValidationException("templateId is required");
        }
        validateRecipient(request.channel(), request.recipient().trim());
        request.scheduledAt().ifPresent(scheduledAt -> {
            if (scheduledAt.isBefore(java.time.Instant.EPOCH)) {
                throw new ValidationException("scheduledAt is invalid");
            }
        });
    }

    private void validateRecipient(ChannelType channel, String recipient) {
        boolean valid = switch (channel) {
            case EMAIL -> EMAIL.matcher(recipient).matches();
            case SMS, WHATSAPP -> E164_PHONE.matcher(recipient).matches();
            case SLACK -> SLACK_CHANNEL.matcher(recipient).matches();
            case PUSH -> DEVICE_TOKEN.matcher(recipient).matches();
        };
        if (!valid) {
            throw new ValidationException("Invalid recipient for channel " + channel + ": " + recipient);
        }
    }
}
