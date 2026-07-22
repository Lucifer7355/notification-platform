package com.notificationplatform.dto;

import com.notificationplatform.domain.ChannelType;
import com.notificationplatform.domain.Priority;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SendNotificationRequest {

    private final ChannelType channel;
    private final String recipient;
    private final String templateId;
    private final Map<String, String> variables;
    private final Priority priority;
    private final Instant scheduledAt;

    private SendNotificationRequest(Builder builder) {
        this.channel = builder.channel;
        this.recipient = builder.recipient;
        this.templateId = builder.templateId;
        this.variables = Collections.unmodifiableMap(new LinkedHashMap<>(builder.variables));
        this.priority = builder.priority;
        this.scheduledAt = builder.scheduledAt;
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

    public Optional<Instant> scheduledAt() {
        return Optional.ofNullable(scheduledAt);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ChannelType channel;
        private String recipient;
        private String templateId;
        private Map<String, String> variables = new LinkedHashMap<>();
        private Priority priority = Priority.NORMAL;
        private Instant scheduledAt;

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

        public Builder putVariable(String key, String value) {
            this.variables.put(key, value);
            return this;
        }

        public Builder priority(Priority priority) {
            this.priority = priority;
            return this;
        }

        public Builder scheduledAt(Instant scheduledAt) {
            this.scheduledAt = scheduledAt;
            return this;
        }

        public SendNotificationRequest build() {
            Objects.requireNonNull(channel, "channel");
            Objects.requireNonNull(recipient, "recipient");
            Objects.requireNonNull(templateId, "templateId");
            Objects.requireNonNull(priority, "priority");
            return new SendNotificationRequest(this);
        }
    }
}
