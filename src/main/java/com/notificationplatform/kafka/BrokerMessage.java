package com.notificationplatform.kafka;

import java.time.Instant;
import java.util.Objects;

public record BrokerMessage(
        String topic,
        long offset,
        String key,
        String payload,
        Instant publishedAt
) {
    public BrokerMessage {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(publishedAt, "publishedAt");
    }
}
