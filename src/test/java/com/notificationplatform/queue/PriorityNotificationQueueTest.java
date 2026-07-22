package com.notificationplatform.queue;

import com.notificationplatform.domain.ChannelType;
import com.notificationplatform.domain.Notification;
import com.notificationplatform.domain.Priority;
import com.notificationplatform.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PriorityNotificationQueueTest {

    @Test
    void poll_ordersByPriorityDescendingThenSequence() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        PriorityNotificationQueue queue = new PriorityNotificationQueue(Duration.ofSeconds(5), clock);

        queue.enqueue(notification("1", Priority.LOW));
        queue.enqueue(notification("2", Priority.CRITICAL));
        queue.enqueue(notification("3", Priority.HIGH));

        assertThat(queue.peekReadyPriorities())
                .containsExactly(Priority.CRITICAL, Priority.HIGH, Priority.LOW);
        assertThat(queue.poll()).get().extracting(Notification::id).isEqualTo("2");
        assertThat(queue.poll()).get().extracting(Notification::id).isEqualTo("3");
        assertThat(queue.poll()).get().extracting(Notification::id).isEqualTo("1");
    }

    @Test
    void poll_unackedMessage_reappearsAfterVisibilityTimeout() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        PriorityNotificationQueue queue = new PriorityNotificationQueue(Duration.ofSeconds(2), clock);

        queue.enqueue(notification("vis-1", Priority.NORMAL));
        assertThat(queue.poll()).isPresent();
        assertThat(queue.readySize()).isZero();
        assertThat(queue.inFlightSize()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(2));
        assertThat(queue.readySize()).isEqualTo(1);
        assertThat(queue.poll()).get().extracting(Notification::id).isEqualTo("vis-1");
    }

    @Test
    void acknowledge_removesInFlightMessage() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        PriorityNotificationQueue queue = new PriorityNotificationQueue(Duration.ofSeconds(2), clock);
        queue.enqueue(notification("ack-1", Priority.NORMAL));
        queue.poll();

        assertThat(queue.acknowledge("ack-1")).isTrue();
        clock.advance(Duration.ofSeconds(5));
        assertThat(queue.readySize()).isZero();
        assertThat(queue.poll()).isEmpty();
    }

    private static Notification notification(String id, Priority priority) {
        return Notification.builder()
                .id(id)
                .channel(ChannelType.EMAIL)
                .recipient("a@b.com")
                .templateId("email-welcome")
                .variables(MapLike.map())
                .priority(priority)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .subject("s")
                .renderedBody("b")
                .build();
    }

    private static final class MapLike {
        private static java.util.Map<String, String> map() {
            return java.util.Map.of();
        }
    }
}
