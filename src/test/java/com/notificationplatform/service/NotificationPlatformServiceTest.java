package com.notificationplatform.service;

import com.notificationplatform.NotificationPlatformFactory;
import com.notificationplatform.config.PlatformConfig;
import com.notificationplatform.domain.ChannelType;
import com.notificationplatform.domain.NotificationStatus;
import com.notificationplatform.domain.Priority;
import com.notificationplatform.dto.NotificationReceipt;
import com.notificationplatform.dto.SendNotificationRequest;
import com.notificationplatform.exception.ValidationException;
import com.notificationplatform.support.MutableClock;
import com.notificationplatform.util.IdGenerator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationPlatformServiceTest {

    @Test
    void acceptDrainProcess_deliversAcrossChannels() {
        NotificationPlatformService platform = NotificationPlatformFactory.createDefault();

        platform.accept(email("a@example.com", Priority.NORMAL));
        platform.accept(SendNotificationRequest.builder()
                .channel(ChannelType.SLACK)
                .recipient("#ops")
                .templateId("slack-deploy")
                .variables(Map.of("service", "billing", "env", "prod", "actor", "bot"))
                .priority(Priority.CRITICAL)
                .build());

        assertThat(platform.drainKafkaToPriorityQueue(10)).isEqualTo(2);
        assertThat(platform.processAvailable(10)).isEqualTo(2);
        assertThat(platform.analytics().snapshot().delivered()).isEqualTo(2);
    }

    @Test
    void processNext_retriesThenDeadLetters() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-22T00:00:00Z"));
        PlatformConfig config = PlatformConfig.builder()
                .maxRetryAttempts(2)
                .initialBackoff(Duration.ofMillis(10))
                .build();

        NotificationPlatformService platform = NotificationPlatformFactory.create(
                clock,
                config,
                n -> true,
                100,
                IdGenerator.sequential("t"));

        platform.accept(email("fail@example.com", Priority.HIGH));
        platform.drainKafkaToPriorityQueue(5);
        platform.processNext();
        assertThat(platform.pendingRetries()).isEqualTo(1);

        clock.advance(Duration.ofMillis(10));
        platform.releaseDueRetries();
        var finalNotification = platform.processNext().orElseThrow();
        assertThat(finalNotification.status()).isEqualTo(NotificationStatus.DEAD_LETTERED);
        assertThat(platform.deadLetterQueue().size()).isEqualTo(1);
        assertThat(platform.messageBroker().snapshot(config.dlqTopic())).hasSize(1);
    }

    @Test
    void accept_scheduledNotification_waitsUntilDue() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-22T00:00:00Z"));
        NotificationPlatformService platform = NotificationPlatformFactory.create(
                clock,
                PlatformConfig.defaults(),
                n -> false,
                100,
                IdGenerator.sequential("s"));

        Instant fireAt = clock.instant().plusSeconds(60);
        NotificationReceipt receipt = platform.accept(SendNotificationRequest.builder()
                .channel(ChannelType.PUSH)
                .recipient("device_token_xyz")
                .templateId("push-alert")
                .variables(Map.of("title", "T", "body", "B"))
                .scheduledAt(fireAt)
                .build());

        assertThat(receipt.status()).isEqualTo(NotificationStatus.SCHEDULED);
        assertThat(platform.drainKafkaToPriorityQueue(5)).isZero();

        clock.advance(Duration.ofSeconds(60));
        assertThat(platform.releaseDueScheduled()).isEqualTo(1);
        assertThat(platform.drainKafkaToPriorityQueue(5)).isEqualTo(1);
        assertThat(platform.processNext()).get().extracting(n -> n.status())
                .isEqualTo(NotificationStatus.DELIVERED);
    }

    @Test
    void accept_sameIdempotencyKey_returnsSameNotification() {
        NotificationPlatformService platform = NotificationPlatformFactory.createDefault();
        SendNotificationRequest request = email("idem@example.com", Priority.NORMAL);

        NotificationReceipt first = platform.accept(request, "key-1");
        NotificationReceipt second = platform.accept(request, "key-1");

        assertThat(second.notificationId()).isEqualTo(first.notificationId());
    }

    @Test
    void accept_rateLimitExceeded_throws() {
        NotificationPlatformService platform = NotificationPlatformFactory.create(
                java.time.Clock.systemUTC(),
                PlatformConfig.defaults(),
                n -> false,
                1,
                IdGenerator.sequential("r"));

        platform.accept(email("rate@example.com", Priority.LOW));
        assertThatThrownBy(() -> platform.accept(email("rate@example.com", Priority.LOW)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Rate limit exceeded");
    }

    @Test
    void priorityQueue_servesCriticalFirst() {
        NotificationPlatformService platform = NotificationPlatformFactory.createDefault();
        platform.accept(email("low@example.com", Priority.LOW));
        platform.accept(email("crit@example.com", Priority.CRITICAL));
        platform.drainKafkaToPriorityQueue(10);

        assertThat(platform.processNext()).get().extracting(n -> n.priority()).isEqualTo(Priority.CRITICAL);
        assertThat(platform.processNext()).get().extracting(n -> n.priority()).isEqualTo(Priority.LOW);
    }

    private static SendNotificationRequest email(String recipient, Priority priority) {
        return SendNotificationRequest.builder()
                .channel(ChannelType.EMAIL)
                .recipient(recipient)
                .templateId("email-welcome")
                .variables(Map.of("name", "User", "product", "NP"))
                .priority(priority)
                .build();
    }
}
