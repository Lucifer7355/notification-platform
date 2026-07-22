package com.notificationplatform.dto;

import com.notificationplatform.domain.ChannelType;
import com.notificationplatform.domain.NotificationStatus;
import com.notificationplatform.domain.Priority;

import java.time.Instant;
import java.util.Optional;

public record NotificationReceipt(
        String notificationId,
        ChannelType channel,
        String recipient,
        Priority priority,
        NotificationStatus status,
        Instant acceptedAt,
        Instant scheduledAt
) {
    public Optional<Instant> scheduledTime() {
        return Optional.ofNullable(scheduledAt);
    }
}
