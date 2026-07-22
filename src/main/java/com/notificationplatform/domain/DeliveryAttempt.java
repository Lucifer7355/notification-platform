package com.notificationplatform.domain;

import java.time.Instant;
import java.util.Objects;

public final class DeliveryAttempt {

    private final String notificationId;
    private final int attemptNumber;
    private final Instant attemptedAt;
    private final boolean success;
    private final String providerResponse;
    private final String error;

    public DeliveryAttempt(
            String notificationId,
            int attemptNumber,
            Instant attemptedAt,
            boolean success,
            String providerResponse,
            String error) {
        this.notificationId = Objects.requireNonNull(notificationId, "notificationId");
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be >= 1");
        }
        this.attemptNumber = attemptNumber;
        this.attemptedAt = Objects.requireNonNull(attemptedAt, "attemptedAt");
        this.success = success;
        this.providerResponse = providerResponse;
        this.error = error;
    }

    public String notificationId() {
        return notificationId;
    }

    public int attemptNumber() {
        return attemptNumber;
    }

    public Instant attemptedAt() {
        return attemptedAt;
    }

    public boolean success() {
        return success;
    }

    public String providerResponse() {
        return providerResponse;
    }

    public String error() {
        return error;
    }
}
