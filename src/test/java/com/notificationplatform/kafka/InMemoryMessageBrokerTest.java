package com.notificationplatform.kafka;

import com.notificationplatform.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryMessageBrokerTest {

    @Test
    void publishPollCommit_tracksLag() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryMessageBroker broker = new InMemoryMessageBroker(clock);

        broker.publish("notifications", "k1", "n1");
        broker.publish("notifications", "k2", "n2");
        assertThat(broker.lag("notifications", "workers")).isEqualTo(2);

        BrokerMessage first = broker.poll("notifications", "workers").orElseThrow();
        assertThat(first.payload()).isEqualTo("n1");
        broker.commit("notifications", "workers", first.offset());
        assertThat(broker.lag("notifications", "workers")).isEqualTo(1);
    }
}
