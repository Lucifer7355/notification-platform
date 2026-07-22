package com.notificationplatform;

import com.notificationplatform.config.PlatformConfig;
import com.notificationplatform.domain.ChannelType;
import com.notificationplatform.domain.Notification;
import com.notificationplatform.domain.Priority;
import com.notificationplatform.dto.NotificationReceipt;
import com.notificationplatform.dto.SendNotificationRequest;
import com.notificationplatform.exception.NotificationException;
import com.notificationplatform.service.NotificationPlatformService;
import com.notificationplatform.util.IdGenerator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.out.println("=".repeat(72));
        System.out.println(" NOTIFICATION PLATFORM - Portfolio Demo Walkthrough");
        System.out.println(" Channels: Email | SMS | WhatsApp | Push | Slack");
        System.out.println(" Features: Priority Queue | Retry | DLQ | Scheduling | Kafka | Redis | Templates | Analytics");
        System.out.println("=".repeat(72));

        scenarioHappyPathMultiChannel();
        scenarioPriorityOrdering();
        scenarioRetryThenDeliver();
        scenarioDeadLetterQueue();
        scenarioScheduling();
        scenarioTemplatesAndValidation();
        scenarioIdempotencyAndRateLimit();
        scenarioAnalyticsReport();

        System.out.println();
        System.out.println("=".repeat(72));
        System.out.println(" Demo complete. Run tests with: mvn test");
        System.out.println("=".repeat(72));
    }

    private static void scenarioHappyPathMultiChannel() {
        header("1) Happy path - all five channels");
        NotificationPlatformService platform = NotificationPlatformFactory.createDefault();

        List<SendNotificationRequest> requests = List.of(
                SendNotificationRequest.builder()
                        .channel(ChannelType.EMAIL)
                        .recipient("ada@example.com")
                        .templateId("email-welcome")
                        .variables(Map.of("name", "Ada", "product", "NotifyX"))
                        .priority(Priority.NORMAL)
                        .build(),
                SendNotificationRequest.builder()
                        .channel(ChannelType.SMS)
                        .recipient("+919876543210")
                        .templateId("sms-otp")
                        .variables(Map.of("otp", "482913", "minutes", "5"))
                        .priority(Priority.HIGH)
                        .build(),
                SendNotificationRequest.builder()
                        .channel(ChannelType.WHATSAPP)
                        .recipient("+919876543210")
                        .templateId("wa-order")
                        .variables(Map.of("name", "Ada", "orderId", "ORD-42", "status", "shipped"))
                        .priority(Priority.NORMAL)
                        .build(),
                SendNotificationRequest.builder()
                        .channel(ChannelType.PUSH)
                        .recipient("device_token_abc123")
                        .templateId("push-alert")
                        .variables(Map.of("title", "Payment received", "body", "₹999 credited"))
                        .priority(Priority.HIGH)
                        .build(),
                SendNotificationRequest.builder()
                        .channel(ChannelType.SLACK)
                        .recipient("#deploys")
                        .templateId("slack-deploy")
                        .variables(Map.of("service", "checkout", "env", "prod", "actor", "ci-bot"))
                        .priority(Priority.CRITICAL)
                        .build());

        for (SendNotificationRequest request : requests) {
            NotificationReceipt receipt = platform.accept(request);
            action("Accepted " + receipt.channel() + " -> " + receipt.recipient()
                    + " id=" + receipt.notificationId() + " status=" + receipt.status());
        }

        int drained = platform.drainKafkaToPriorityQueue(20);
        result("Kafka -> priority queue drained messages: " + drained);
        why("Ingress is Kafka-backed so producers stay fast and workers scale independently");

        int processed = platform.processAvailable(20);
        result("Processed deliveries: " + processed);
        for (Notification notification : List.of(
                platform.findById("ntf-1").orElseThrow(),
                platform.findById("ntf-5").orElseThrow())) {
            result(notification.channel() + " final status=" + notification.status()
                    + " body=\"" + notification.renderedBody() + "\"");
        }
    }

    private static void scenarioPriorityOrdering() {
        header("2) Priority queue - CRITICAL before LOW");
        NotificationPlatformService platform = NotificationPlatformFactory.createDefault();

        platform.accept(request(ChannelType.EMAIL, "low@example.com", "email-welcome",
                Map.of("name", "Low", "product", "X"), Priority.LOW));
        platform.accept(request(ChannelType.EMAIL, "critical@example.com", "email-welcome",
                Map.of("name", "Crit", "product", "X"), Priority.CRITICAL));
        platform.accept(request(ChannelType.EMAIL, "high@example.com", "email-welcome",
                Map.of("name", "High", "product", "X"), Priority.HIGH));

        platform.drainKafkaToPriorityQueue(10);
        action("Published LOW, CRITICAL, HIGH into Kafka then drained to priority queue");
        result("Ready order by priority: " + platform.priorityQueue().peekReadyPriorities());
        why("Heap orders by priority weight desc, then FIFO sequence for ties");

        Notification first = platform.processNext().orElseThrow();
        Notification second = platform.processNext().orElseThrow();
        Notification third = platform.processNext().orElseThrow();
        result("Consumed order: " + first.priority() + " -> " + second.priority() + " -> " + third.priority());
    }

    private static void scenarioRetryThenDeliver() {
        header("3) Retry with exponential backoff - fail twice, succeed third");
        AtomicInteger attempts = new AtomicInteger();
        MutableClock clock = new MutableClock(Instant.parse("2026-07-22T10:00:00Z"));
        PlatformConfig config = PlatformConfig.builder()
                .maxRetryAttempts(3)
                .initialBackoff(Duration.ofMillis(100))
                .backoffMultiplier(2.0)
                .maxBackoff(Duration.ofSeconds(1))
                .build();

        NotificationPlatformService platform = NotificationPlatformFactory.create(
                clock,
                config,
                notification -> attempts.incrementAndGet() < 3,
                1000,
                IdGenerator.sequential("retry"));

        NotificationReceipt receipt = platform.accept(request(
                ChannelType.SMS, "+911234567890", "sms-otp",
                Map.of("otp", "111222", "minutes", "2"), Priority.HIGH));
        platform.drainKafkaToPriorityQueue(5);

        action("First delivery attempt (provider down)");
        Notification afterFail1 = platform.processNext().orElseThrow();
        result("status=" + afterFail1.status() + " attempts=" + afterFail1.attemptCount()
                + " pendingRetries=" + platform.pendingRetries());
        why("attemptCount < maxAttempts => RETRYING with backoff delay");

        clock.advance(Duration.ofMillis(100));
        action("Advance clock by 100ms (first backoff elapsed)");
        platform.releaseDueRetries();
        Notification afterFail2 = platform.processNext().orElseThrow();
        result("status=" + afterFail2.status() + " attempts=" + afterFail2.attemptCount());

        clock.advance(Duration.ofMillis(200));
        action("Advance clock by 200ms (second backoff elapsed)");
        platform.releaseDueRetries();
        Notification delivered = platform.processNext().orElseThrow();
        result("status=" + delivered.status() + " attempts=" + delivered.attemptCount()
                + " id=" + receipt.notificationId());
        why("Same notification id reused across retries - at-least-once with idempotent providers in prod");
    }

    private static void scenarioDeadLetterQueue() {
        header("4) DLQ - exhaust retries, publish to notifications.dlq");
        MutableClock clock = new MutableClock(Instant.parse("2026-07-22T11:00:00Z"));
        PlatformConfig config = PlatformConfig.builder()
                .maxRetryAttempts(2)
                .initialBackoff(Duration.ofMillis(50))
                .build();

        NotificationPlatformService platform = NotificationPlatformFactory.create(
                clock,
                config,
                notification -> true,
                1000,
                IdGenerator.sequential("dlq"));

        platform.accept(request(
                ChannelType.WHATSAPP, "+919999888877", "wa-order",
                Map.of("name", "Sam", "orderId", "ORD-9", "status", "failed-provider"),
                Priority.NORMAL));
        platform.drainKafkaToPriorityQueue(5);

        platform.processNext();
        clock.advance(Duration.ofMillis(50));
        platform.releaseDueRetries();
        Notification dead = platform.processNext().orElseThrow();

        result("final status=" + dead.status() + " attempts=" + dead.attemptCount());
        result("DLQ size=" + platform.deadLetterQueue().size()
                + " reason=" + platform.deadLetterQueue().snapshot().getFirst().reason());
        result("Kafka DLQ topic messages=" + platform.messageBroker().snapshot(config.dlqTopic()).size());
        why("Poison messages leave the hot path so healthy traffic is not blocked");
    }

    private static void scenarioScheduling() {
        header("5) Scheduling - hold until fire time, then Kafka + deliver");
        MutableClock clock = new MutableClock(Instant.parse("2026-07-22T12:00:00Z"));
        NotificationPlatformService platform = NotificationPlatformFactory.create(
                clock,
                PlatformConfig.defaults(),
                notification -> false,
                1000,
                IdGenerator.sequential("sched"));

        Instant fireAt = clock.instant().plusSeconds(30);
        NotificationReceipt receipt = platform.accept(SendNotificationRequest.builder()
                .channel(ChannelType.PUSH)
                .recipient("device_token_sched01")
                .templateId("push-alert")
                .variables(Map.of("title", "Reminder", "body", "Meeting in 5 minutes"))
                .priority(Priority.NORMAL)
                .scheduledAt(fireAt)
                .build());

        action("Accepted scheduled notification for " + fireAt);
        result("status=" + receipt.status() + " schedulerPending=" + platform.scheduler().pendingCount());
        result("Kafka lag before fire=" + platform.messageBroker().lag("notifications", "notification-workers"));

        clock.advance(Duration.ofSeconds(30));
        int released = platform.releaseDueScheduled();
        action("Clock advanced 30s; released due scheduled=" + released);
        platform.drainKafkaToPriorityQueue(5);
        Notification delivered = platform.processNext().orElseThrow();
        result("delivered status=" + delivered.status() + " subject=" + delivered.subject());
        why("Scheduler is a time-wheel/heap in-process; at scale use Kafka delayed topics or a schedule store");
    }

    private static void scenarioTemplatesAndValidation() {
        header("6) Templates + validation - missing vars and bad recipients");
        NotificationPlatformService platform = NotificationPlatformFactory.createDefault();

        try {
            platform.accept(SendNotificationRequest.builder()
                    .channel(ChannelType.EMAIL)
                    .recipient("ada@example.com")
                    .templateId("email-welcome")
                    .variables(Map.of("name", "Ada"))
                    .build());
            result("ERROR: expected ValidationException for missing product");
        } catch (NotificationException ex) {
            result("Caught expected: " + ex.getMessage());
            why("Fail-fast before Kafka - bad payloads never enter the pipeline");
        }

        try {
            platform.accept(SendNotificationRequest.builder()
                    .channel(ChannelType.SMS)
                    .recipient("not-a-phone")
                    .templateId("sms-otp")
                    .variables(Map.of("otp", "1", "minutes", "1"))
                    .build());
            result("ERROR: expected ValidationException for bad phone");
        } catch (NotificationException ex) {
            result("Caught expected: " + ex.getMessage());
        }
    }

    private static void scenarioIdempotencyAndRateLimit() {
        header("7) Redis - idempotency key + per-recipient rate limit");
        NotificationPlatformService platform = NotificationPlatformFactory.create(
                Clock.systemUTC(),
                PlatformConfig.defaults(),
                notification -> false,
                2,
                IdGenerator.sequential("redis"));

        SendNotificationRequest request = request(
                ChannelType.SLACK, "#alerts", "slack-deploy",
                Map.of("service", "api", "env", "stage", "actor", "dev"),
                Priority.NORMAL);

        NotificationReceipt first = platform.accept(request, "client-key-1");
        NotificationReceipt second = platform.accept(request, "client-key-1");
        action("Accepted same request twice with idempotency key client-key-1");
        result("firstId=" + first.notificationId() + " secondId=" + second.notificationId()
                + " same=" + first.notificationId().equals(second.notificationId()));
        why("Redis SET NX style idempotency prevents duplicate fan-out on client retries");

        platform.accept(request(ChannelType.EMAIL, "rate@example.com", "email-welcome",
                Map.of("name", "R", "product", "X"), Priority.LOW));
        platform.accept(request(ChannelType.EMAIL, "rate@example.com", "email-welcome",
                Map.of("name", "R", "product", "X"), Priority.LOW));
        try {
            platform.accept(request(ChannelType.EMAIL, "rate@example.com", "email-welcome",
                    Map.of("name", "R", "product", "X"), Priority.LOW));
            result("ERROR: expected rate limit");
        } catch (NotificationException ex) {
            result("Caught expected: " + ex.getMessage());
            why("Redis INCR + TTL window enforces soft rate limits before queue pressure builds");
        }
    }

    private static void scenarioAnalyticsReport() {
        header("8) Analytics snapshot - delivery funnel metrics");
        NotificationPlatformService platform = NotificationPlatformFactory.createDefault();
        platform.accept(request(ChannelType.EMAIL, "a@example.com", "email-welcome",
                Map.of("name", "A", "product", "P"), Priority.CRITICAL));
        platform.accept(request(ChannelType.SMS, "+911111111111", "sms-otp",
                Map.of("otp", "9", "minutes", "1"), Priority.LOW));
        platform.drainKafkaToPriorityQueue(10);
        platform.processAvailable(10);
        result(platform.analytics().renderReport());
        why("Counters power dashboards; at billions/day export to ClickHouse/Druid via Kafka metrics topic");
    }

    private static SendNotificationRequest request(
            ChannelType channel,
            String recipient,
            String templateId,
            Map<String, String> variables,
            Priority priority) {
        return SendNotificationRequest.builder()
                .channel(channel)
                .recipient(recipient)
                .templateId(templateId)
                .variables(variables)
                .priority(priority)
                .build();
    }

    private static void header(String title) {
        System.out.println();
        System.out.println("-".repeat(72));
        System.out.println("[SCENARIO] " + title);
        System.out.println("-".repeat(72));
    }

    private static void action(String message) {
        System.out.println("[ACTION]  " + message);
    }

    private static void result(String message) {
        System.out.println("[RESULT]  " + message);
    }

    private static void why(String message) {
        System.out.println("[WHY]     " + message);
    }

    /**
     * Test/demo clock that can be advanced without sleeping.
     */
    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        private final ZoneOffset zone = ZoneOffset.UTC;

        MutableClock(Instant start) {
            this.instant = new AtomicReference<>(start);
        }

        void advance(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
        }

        @Override
        public ZoneOffset getZone() {
            return zone;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
