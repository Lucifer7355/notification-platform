package com.notificationplatform.kafka;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public final class InMemoryMessageBroker implements MessageBroker {

    private final Clock clock;
    private final ConcurrentHashMap<String, TopicLog> topics = new ConcurrentHashMap<>();

    public InMemoryMessageBroker(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void publish(String topic, String key, String payload) {
        topicLog(topic).append(key, payload, clock.instant());
    }

    @Override
    public Optional<BrokerMessage> poll(String topic, String consumerGroup) {
        return topicLog(topic).poll(consumerGroup);
    }

    @Override
    public void commit(String topic, String consumerGroup, long offset) {
        topicLog(topic).commit(consumerGroup, offset);
    }

    @Override
    public List<BrokerMessage> snapshot(String topic) {
        return topicLog(topic).snapshot();
    }

    @Override
    public int lag(String topic, String consumerGroup) {
        return topicLog(topic).lag(consumerGroup);
    }

    private TopicLog topicLog(String topic) {
        return topics.computeIfAbsent(Objects.requireNonNull(topic, "topic"), ignored -> new TopicLog(topic));
    }

    private static final class TopicLog {
        private final String topic;
        private final CopyOnWriteArrayList<BrokerMessage> messages = new CopyOnWriteArrayList<>();
        private final AtomicLong nextOffset = new AtomicLong(0);
        private final Map<String, Long> committedOffsets = new ConcurrentHashMap<>();
        private final Map<String, Long> fetchOffsets = new ConcurrentHashMap<>();
        private final ReentrantLock lock = new ReentrantLock();

        private TopicLog(String topic) {
            this.topic = topic;
        }

        private void append(String key, String payload, java.time.Instant publishedAt) {
            lock.lock();
            try {
                long offset = nextOffset.getAndIncrement();
                messages.add(new BrokerMessage(topic, offset, key, payload, publishedAt));
            } finally {
                lock.unlock();
            }
        }

        private Optional<BrokerMessage> poll(String consumerGroup) {
            lock.lock();
            try {
                long fetchOffset = fetchOffsets.getOrDefault(consumerGroup, committedOffsets.getOrDefault(consumerGroup, 0L));
                if (fetchOffset >= messages.size()) {
                    return Optional.empty();
                }
                BrokerMessage message = messages.get((int) fetchOffset);
                fetchOffsets.put(consumerGroup, fetchOffset + 1);
                return Optional.of(message);
            } finally {
                lock.unlock();
            }
        }

        private void commit(String consumerGroup, long offset) {
            lock.lock();
            try {
                committedOffsets.put(consumerGroup, offset + 1);
                fetchOffsets.put(consumerGroup, offset + 1);
            } finally {
                lock.unlock();
            }
        }

        private List<BrokerMessage> snapshot() {
            return new ArrayList<>(messages);
        }

        private int lag(String consumerGroup) {
            long committed = committedOffsets.getOrDefault(consumerGroup, 0L);
            return (int) Math.max(0, messages.size() - committed);
        }
    }
}
