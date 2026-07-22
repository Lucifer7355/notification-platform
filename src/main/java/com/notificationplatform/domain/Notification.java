package com.notificationplatform.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class Notification {

    private final String id;
    private final ChannelType channel;
    private final String recipient;
    private final String templateId;
    private final Map<String, String> variables;
    private final Priority priority;
    private final Instant createdAt;
    private final Instant scheduledAt;
    private final String subject;
    private final String renderedBody;
    private volatile NotificationStatus status;
    private volatile int attemptCount;
    private volatile String lastError;
    private volatile Instant lastAttemptAt;
    private volatile Instant deliveredAt;

    private Notification(Builder builder) {
        this.id = builder.id;
        this.channel = builder.channel;
        this.recipient = builder.recipient;
        this.templateId = builder.templateId;
        this.variables = Collections.unmodifiableMap(new LinkedHashMap<>(builder.variables));
        this.priority = builder.priority;
        this.createdAt = builder.createdAt;
        this.scheduledAt = builder.scheduledAt;
        this.subject = builder.subject;
        this.renderedBody = builder.renderedBody;
        this.status = builder.status;
        this.attemptCount = builder.attemptCount;
        this.lastError = builder.lastError;
        this.lastAttemptAt = builder.lastAttemptAt;
        this.deliveredAt = builder.deliveredAt;
    }

    public String id() {
        return id;
    }

    public ChannelType channel() {
        return channel;
    }

    public String recipient() {
        return recipient;
    }

    public String templateId() {
        return templateId;
    }

    public Map<String, String> variables() {
        return variables;
    }

    public Priority priority() {
        return priority;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Optional<Instant> scheduledAt() {
        return Optional.ofNullable(scheduledAt);
    }

    public String subject() {
        return subject;
    }

    public String renderedBody() {
        return renderedBody;
    }

    public NotificationStatus status() {
        return status;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public Optional<String> lastError() {
        return Optional.ofNullable(lastError);
    }

    public Optional<Instant> lastAttemptAt() {
        return Optional.ofNullable(lastAttemptAt);
    }

    public Optional<Instant> deliveredAt() {
        return Optional.ofNullable(deliveredAt);
    }

    public synchronized void markQueued() {
        this.status = NotificationStatus.QUEUED;
    }

    public synchronized void markScheduled() {
        this.status = NotificationStatus.SCHEDULED;
    }

    public synchronized void markSending(Instant now) {
        this.status = NotificationStatus.SENDING;
        this.attemptCount++;
        this.lastAttemptAt = now;
    }

    public synchronized void markDelivered(Instant now) {
        this.status = NotificationStatus.DELIVERED;
        this.deliveredAt = now;
        this.lastError = null;
    }

    public synchronized void markRetrying(String error) {
        this.status = NotificationStatus.RETRYING;
        this.lastError = Objects.requireNonNull(error, "error");
    }

    public synchronized void markFailed(String error) {
        this.status = NotificationStatus.FAILED;
        this.lastError = Objects.requireNonNull(error, "error");
    }

    public synchronized void markDeadLettered(String error) {
        this.status = NotificationStatus.DEAD_LETTERED;
        this.lastError = Objects.requireNonNull(error, "error");
    }

    public synchronized Notification copyForReplay() {
        return new Builder()
                .id(id)
                .channel(channel)
                .recipient(recipient)
                .templateId(templateId)
                .variables(variables)
                .priority(priority)
                .createdAt(createdAt)
                .scheduledAt(scheduledAt)
                .subject(subject)
                .renderedBody(renderedBody)
                .status(status)
                .attemptCount(attemptCount)
                .lastError(lastError)
                .lastAttemptAt(lastAttemptAt)
                .deliveredAt(deliveredAt)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private ChannelType channel;
        private String recipient;
        private String templateId;
        private Map<String, String> variables = new LinkedHashMap<>();
        private Priority priority = Priority.NORMAL;
        private Instant createdAt = Instant.EPOCH;
        private Instant scheduledAt;
        private String subject = "";
        private String renderedBody = "";
        private NotificationStatus status = NotificationStatus.ACCEPTED;
        private int attemptCount;
        private String lastError;
        private Instant lastAttemptAt;
        private Instant deliveredAt;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder channel(ChannelType channel) {
            this.channel = channel;
            return this;
        }

        public Builder recipient(String recipient) {
            this.recipient = recipient;
            return this;
        }

        public Builder templateId(String templateId) {
            this.templateId = templateId;
            return this;
        }

        public Builder variables(Map<String, String> variables) {
            this.variables = new LinkedHashMap<>(variables == null ? Map.of() : variables);
            return this;
        }

        public Builder priority(Priority priority) {
            this.priority = priority;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder scheduledAt(Instant scheduledAt) {
            this.scheduledAt = scheduledAt;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder renderedBody(String renderedBody) {
            this.renderedBody = renderedBody;
            return this;
        }

        public Builder status(NotificationStatus status) {
            this.status = status;
            return this;
        }

        public Builder attemptCount(int attemptCount) {
            this.attemptCount = attemptCount;
            return this;
        }

        public Builder lastError(String lastError) {
            this.lastError = lastError;
            return this;
        }

        public Builder lastAttemptAt(Instant lastAttemptAt) {
            this.lastAttemptAt = lastAttemptAt;
            return this;
        }

        public Builder deliveredAt(Instant deliveredAt) {
            this.deliveredAt = deliveredAt;
            return this;
        }

        public Notification build() {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(channel, "channel");
            Objects.requireNonNull(recipient, "recipient");
            Objects.requireNonNull(templateId, "templateId");
            Objects.requireNonNull(priority, "priority");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(renderedBody, "renderedBody");
            Objects.requireNonNull(status, "status");
            return new Notification(this);
        }
    }
}
