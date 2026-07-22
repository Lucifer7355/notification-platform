package com.notificationplatform.kafka;

import java.util.List;
import java.util.Optional;

/**
 * Minimal Kafka-like broker abstraction used by the platform.
 * Swap InMemoryMessageBroker with a real Kafka client adapter in production.
 */
public interface MessageBroker {

    void publish(String topic, String key, String payload);

    Optional<BrokerMessage> poll(String topic, String consumerGroup);

    void commit(String topic, String consumerGroup, long offset);

    List<BrokerMessage> snapshot(String topic);

    int lag(String topic, String consumerGroup);
}
