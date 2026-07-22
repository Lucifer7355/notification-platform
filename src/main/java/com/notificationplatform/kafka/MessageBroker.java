package com.notificationplatform.kafka;

import java.util.List;
import java.util.Optional;

/**
 * Kafka producer/admin port used by the platform.
 * Consumption is handled by Spring {@code @KafkaListener}.
 */
public interface MessageBroker {

    void publish(String topic, String key, String payload);

    Optional<BrokerMessage> poll(String topic, String consumerGroup);

    void commit(String topic, String consumerGroup, long offset);

    List<BrokerMessage> snapshot(String topic);

    int lag(String topic, String consumerGroup);
}
