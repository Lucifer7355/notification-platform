package com.notificationplatform.config;

import java.time.Duration;
import java.util.Objects;

public final class PlatformConfig {

    private final int maxRetryAttempts;
    private final Duration initialBackoff;
    private final double backoffMultiplier;
    private final Duration maxBackoff;
    private final Duration visibilityTimeout;
    private final int workerCount;
    private final String kafkaTopic;
    private final String dlqTopic;
    private final Duration schedulerTick;

    private PlatformConfig(Builder builder) {
        this.maxRetryAttempts = builder.maxRetryAttempts;
        this.initialBackoff = builder.initialBackoff;
        this.backoffMultiplier = builder.backoffMultiplier;
        this.maxBackoff = builder.maxBackoff;
        this.visibilityTimeout = builder.visibilityTimeout;
        this.workerCount = builder.workerCount;
        this.kafkaTopic = builder.kafkaTopic;
        this.dlqTopic = builder.dlqTopic;
        this.schedulerTick = builder.schedulerTick;
    }

    public int maxRetryAttempts() {
        return maxRetryAttempts;
    }

    public Duration initialBackoff() {
        return initialBackoff;
    }

    public double backoffMultiplier() {
        return backoffMultiplier;
    }

    public Duration maxBackoff() {
        return maxBackoff;
    }

    public Duration visibilityTimeout() {
        return visibilityTimeout;
    }

    public int workerCount() {
        return workerCount;
    }

    public String kafkaTopic() {
        return kafkaTopic;
    }

    public String dlqTopic() {
        return dlqTopic;
    }

    public Duration schedulerTick() {
        return schedulerTick;
    }

    public static PlatformConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int maxRetryAttempts = 3;
        private Duration initialBackoff = Duration.ofMillis(100);
        private double backoffMultiplier = 2.0;
        private Duration maxBackoff = Duration.ofSeconds(5);
        private Duration visibilityTimeout = Duration.ofSeconds(2);
        private int workerCount = 2;
        private String kafkaTopic = "notifications";
        private String dlqTopic = "notifications.dlq";
        private Duration schedulerTick = Duration.ofMillis(50);

        public Builder maxRetryAttempts(int maxRetryAttempts) {
            this.maxRetryAttempts = maxRetryAttempts;
            return this;
        }

        public Builder initialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
            return this;
        }

        public Builder backoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
            return this;
        }

        public Builder maxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
            return this;
        }

        public Builder visibilityTimeout(Duration visibilityTimeout) {
            this.visibilityTimeout = visibilityTimeout;
            return this;
        }

        public Builder workerCount(int workerCount) {
            this.workerCount = workerCount;
            return this;
        }

        public Builder kafkaTopic(String kafkaTopic) {
            this.kafkaTopic = kafkaTopic;
            return this;
        }

        public Builder dlqTopic(String dlqTopic) {
            this.dlqTopic = dlqTopic;
            return this;
        }

        public Builder schedulerTick(Duration schedulerTick) {
            this.schedulerTick = schedulerTick;
            return this;
        }

        public PlatformConfig build() {
            if (maxRetryAttempts < 1) {
                throw new IllegalArgumentException("maxRetryAttempts must be >= 1");
            }
            if (workerCount < 1) {
                throw new IllegalArgumentException("workerCount must be >= 1");
            }
            if (backoffMultiplier < 1.0) {
                throw new IllegalArgumentException("backoffMultiplier must be >= 1.0");
            }
            Objects.requireNonNull(initialBackoff, "initialBackoff");
            Objects.requireNonNull(maxBackoff, "maxBackoff");
            Objects.requireNonNull(visibilityTimeout, "visibilityTimeout");
            Objects.requireNonNull(kafkaTopic, "kafkaTopic");
            Objects.requireNonNull(dlqTopic, "dlqTopic");
            Objects.requireNonNull(schedulerTick, "schedulerTick");
            return new PlatformConfig(this);
        }
    }
}
