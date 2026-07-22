package com.notificationplatform.validation;

import com.notificationplatform.domain.ChannelType;
import com.notificationplatform.domain.Priority;
import com.notificationplatform.dto.SendNotificationRequest;
import com.notificationplatform.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationRequestValidatorTest {

    private final NotificationRequestValidator validator = new NotificationRequestValidator();

    @Test
    void validate_validEmail_passes() {
        assertThatCode(() -> validator.validate(SendNotificationRequest.builder()
                .channel(ChannelType.EMAIL)
                .recipient("user@example.com")
                .templateId("email-welcome")
                .variables(Map.of())
                .priority(Priority.NORMAL)
                .build())).doesNotThrowAnyException();
    }

    @Test
    void validate_invalidSmsRecipient_throws() {
        assertThatThrownBy(() -> validator.validate(SendNotificationRequest.builder()
                .channel(ChannelType.SMS)
                .recipient("99999")
                .templateId("sms-otp")
                .build()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid recipient");
    }
}
