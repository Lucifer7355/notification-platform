package com.notificationplatform.domain;

import java.time.Instant;
import java.util.Objects;

public final class DeadLetterRecord {

    private final String notificationId;
    private final Notification notification;
    private final String reason;
    private final Instant deadLetteredAt;
    private final int totalAttempts;

    public DeadLetterRecord(
            String notificationId,
            Notification notification,
            String reason,
            Instant deadLetteredAt,
            int totalAttempts) {
        this.notificationId = Objects.requireNonNull(notificationId, "notificationId");
        this.notification = Objects.requireNonNull(notification, "notification");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.deadLetteredAt = Objects.requireNonNull(deadLetteredAt, "deadLetteredAt");
        this.totalAttempts = totalAttempts;
    }

    public String notificationId() {
        return notificationId;
    }

    public Notification notification() {
        return notification;
    }

    public String reason() {
        return reason;
    }

    public Instant deadLetteredAt() {
        return deadLetteredAt;
    }

    public int totalAttempts() {
        return totalAttempts;
    }
}
