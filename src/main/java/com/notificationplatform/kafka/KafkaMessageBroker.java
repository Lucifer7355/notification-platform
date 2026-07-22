package com.notificationplatform.kafka;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class KafkaMessageBroker implements MessageBroker {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaAdmin kafkaAdmin;

    public KafkaMessageBroker(KafkaTemplate<String, String> kafkaTemplate, KafkaAdmin kafkaAdmin) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    public void publish(String topic, String key, String payload) {
        kafkaTemplate.send(topic, key, payload).join();
    }

    @Override
    public Optional<BrokerMessage> poll(String topic, String consumerGroup) {
        // Consumption is handled by Spring @KafkaListener; poll is not used in production path.
        return Optional.empty();
    }

    @Override
    public void commit(String topic, String consumerGroup, long offset) {
        // Offset commits are handled by the Spring Kafka listener container.
    }

    @Override
    public List<BrokerMessage> snapshot(String topic) {
        return List.of();
    }

    @Override
    public int lag(String topic, String consumerGroup) {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            var partitions = admin.describeTopics(List.of(topic)).allTopicNames().get(5, TimeUnit.SECONDS)
                    .get(topic)
                    .partitions()
                    .stream()
                    .map(p -> new TopicPartition(topic, p.partition()))
                    .toList();

            Map<TopicPartition, OffsetSpec> latestSpec = new HashMap<>();
            for (TopicPartition tp : partitions) {
                latestSpec.put(tp, OffsetSpec.latest());
            }
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
                    admin.listOffsets(latestSpec).all().get(5, TimeUnit.SECONDS);

            Map<TopicPartition, OffsetAndMetadata> committed =
                    admin.listConsumerGroupOffsets(consumerGroup)
                            .partitionsToOffsetAndMetadata()
                            .get(5, TimeUnit.SECONDS);

            long lag = 0;
            for (TopicPartition tp : partitions) {
                long end = endOffsets.get(tp).offset();
                OffsetAndMetadata meta = committed.get(tp);
                long current = meta == null ? 0 : meta.offset();
                lag += Math.max(0, end - current);
            }
            return (int) Math.min(Integer.MAX_VALUE, lag);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return -1;
        } catch (ExecutionException | TimeoutException | RuntimeException ex) {
            return -1;
        }
    }

    public List<String> describeTopicsSafe(String... topics) {
        List<String> names = new ArrayList<>();
        Collections.addAll(names, topics);
        return names;
    }

    public BrokerMessage publishedAt(String topic, long offset, String key, String payload) {
        return new BrokerMessage(topic, offset, key, payload, Instant.now());
    }
}
