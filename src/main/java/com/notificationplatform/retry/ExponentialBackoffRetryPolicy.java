package com.notificationplatform.retry;

import com.notificationplatform.config.PlatformConfig;

import java.time.Duration;
import java.util.Objects;

public final class ExponentialBackoffRetryPolicy implements RetryPolicy {

    private final int maxAttempts;
    private final Duration initialBackoff;
    private final double multiplier;
    private final Duration maxBackoff;

    public ExponentialBackoffRetryPolicy(PlatformConfig config) {
        Objects.requireNonNull(config, "config");
        this.maxAttempts = config.maxRetryAttempts();
        this.initialBackoff = config.initialBackoff();
        this.multiplier = config.backoffMultiplier();
        this.maxBackoff = config.maxBackoff();
    }

    public ExponentialBackoffRetryPolicy(
            int maxAttempts,
            Duration initialBackoff,
            double multiplier,
            Duration maxBackoff) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.maxAttempts = maxAttempts;
        this.initialBackoff = Objects.requireNonNull(initialBackoff, "initialBackoff");
        this.multiplier = multiplier;
        this.maxBackoff = Objects.requireNonNull(maxBackoff, "maxBackoff");
    }

    @Override
    public boolean shouldRetry(int attemptCount) {
        return attemptCount < maxAttempts;
    }

    @Override
    public Duration nextDelay(int attemptCount) {
        if (attemptCount < 1) {
            throw new IllegalArgumentException("attemptCount must be >= 1");
        }
        double factor = Math.pow(multiplier, attemptCount - 1);
        long millis = Math.min(
                maxBackoff.toMillis(),
                Math.round(initialBackoff.toMillis() * factor));
        return Duration.ofMillis(Math.max(1, millis));
    }

    @Override
    public int maxAttempts() {
        return maxAttempts;
    }
}
