package com.notificationplatform.service;

import com.notificationplatform.analytics.AnalyticsService;
import com.notificationplatform.channel.ChannelSendResult;
import com.notificationplatform.config.PlatformConfig;
import com.notificationplatform.dlq.DeadLetterQueue;
import com.notificationplatform.domain.Notification;
import com.notificationplatform.domain.NotificationStatus;
import com.notificationplatform.dto.NotificationReceipt;
import com.notificationplatform.dto.SendNotificationRequest;
import com.notificationplatform.exception.ValidationException;
import com.notificationplatform.kafka.BrokerMessage;
import com.notificationplatform.kafka.MessageBroker;
import com.notificationplatform.queue.PriorityNotificationQueue;
import com.notificationplatform.redis.CacheStore;
import com.notificationplatform.repository.NotificationRepository;
import com.notificationplatform.retry.RetryPolicy;
import com.notificationplatform.scheduling.NotificationScheduler;
import com.notificationplatform.template.TemplateRenderer;
import com.notificationplatform.util.IdGenerator;
import com.notificationplatform.validation.NotificationRequestValidator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.locks.ReentrantLock;

public final class NotificationPlatformService {

    private static final String CONSUMER_GROUP = "notification-workers";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private static final long DEFAULT_RATE_LIMIT = 1000;

    private final PlatformConfig config;
    private final NotificationRequestValidator validator;
    private final TemplateService templateService;
    private final NotificationRepository notificationRepository;
    private final MessageBroker messageBroker;
    private final PriorityNotificationQueue priorityQueue;
    private final NotificationScheduler scheduler;
    private final DeliveryService deliveryService;
    private final RetryPolicy retryPolicy;
    private final DeadLetterQueue deadLetterQueue;
    private final AnalyticsService analyticsService;
    private final CacheStore cacheStore;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final PriorityQueue<DelayedRetry> retryHeap;
    private final ReentrantLock retryLock = new ReentrantLock();
    private final long rateLimitPerRecipient;

    public NotificationPlatformService(
            PlatformConfig config,
            NotificationRequestValidator validator,
            TemplateService templateService,
            NotificationRepository notificationRepository,
            MessageBroker messageBroker,
            PriorityNotificationQueue priorityQueue,
            NotificationScheduler scheduler,
            DeliveryService deliveryService,
            RetryPolicy retryPolicy,
            DeadLetterQueue deadLetterQueue,
            AnalyticsService analyticsService,
            CacheStore cacheStore,
            IdGenerator idGenerator,
            Clock clock) {
        this(config, validator, templateService, notificationRepository, messageBroker, priorityQueue,
                scheduler, deliveryService, retryPolicy, deadLetterQueue, analyticsService, cacheStore,
                idGenerator, clock, DEFAULT_RATE_LIMIT);
    }

    public NotificationPlatformService(
            PlatformConfig config,
            NotificationRequestValidator validator,
            TemplateService templateService,
            NotificationRepository notificationRepository,
            MessageBroker messageBroker,
            PriorityNotificationQueue priorityQueue,
            NotificationScheduler scheduler,
            DeliveryService deliveryService,
            RetryPolicy retryPolicy,
            DeadLetterQueue deadLetterQueue,
            AnalyticsService analyticsService,
            CacheStore cacheStore,
            IdGenerator idGenerator,
            Clock clock,
            long rateLimitPerRecipient) {
        this.config = Objects.requireNonNull(config, "config");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.templateService = Objects.requireNonNull(templateService, "templateService");
        this.notificationRepository = Objects.requireNonNull(notificationRepository, "notificationRepository");
        this.messageBroker = Objects.requireNonNull(messageBroker, "messageBroker");
        this.priorityQueue = Objects.requireNonNull(priorityQueue, "priorityQueue");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.deliveryService = Objects.requireNonNull(deliveryService, "deliveryService");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.deadLetterQueue = Objects.requireNonNull(deadLetterQueue, "deadLetterQueue");
        this.analyticsService = Objects.requireNonNull(analyticsService, "analyticsService");
        this.cacheStore = Objects.requireNonNull(cacheStore, "cacheStore");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.rateLimitPerRecipient = rateLimitPerRecipient;
        this.retryHeap = new PriorityQueue<>(Comparator.comparing(DelayedRetry::readyAt));
    }

    public NotificationReceipt accept(SendNotificationRequest request) {
        return accept(request, null);
    }

    public NotificationReceipt accept(SendNotificationRequest request, String idempotencyKey) {
        Objects.requireNonNull(request, "request");
        validator.validate(request);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String cacheKey = "idempotency:" + idempotencyKey;
            Optional<String> existing = cacheStore.get(cacheKey);
            if (existing.isPresent()) {
                Notification existingNotification = notificationRepository.findById(existing.get())
                        .orElseThrow(() -> new ValidationException("Idempotency key points to missing notification"));
                return toReceipt(existingNotification);
            }
        }

        enforceRateLimit(request);

        var template = templateService.require(request.templateId());
        if (template.channel() != request.channel()) {
            throw new ValidationException(
                    "Template channel mismatch: template=" + template.channel() + ", request=" + request.channel());
        }

        TemplateRenderer.RenderedTemplate rendered = templateService.render(request.templateId(), request.variables());
        Instant now = clock.instant();
        String id = idGenerator.nextId();

        Notification notification = Notification.builder()
                .id(id)
                .channel(request.channel())
                .recipient(request.recipient().trim())
                .templateId(request.templateId())
                .variables(request.variables())
                .priority(request.priority())
                .createdAt(now)
                .scheduledAt(request.scheduledAt().orElse(null))
                .subject(rendered.subject())
                .renderedBody(rendered.body())
                .status(NotificationStatus.ACCEPTED)
                .build();

        notificationRepository.save(notification);
        analyticsService.recordAccepted(notification.channel(), notification.priority());

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            boolean first = cacheStore.setIfAbsent("idempotency:" + idempotencyKey, notification.id(), IDEMPOTENCY_TTL);
            if (!first) {
                Optional<String> winner = cacheStore.get("idempotency:" + idempotencyKey);
                if (winner.isPresent()) {
                    return notificationRepository.findById(winner.get())
                            .map(this::toReceipt)
                            .orElse(toReceipt(notification));
                }
            }
        }

        if (request.scheduledAt().isPresent() && request.scheduledAt().get().isAfter(now)) {
            scheduler.schedule(notification, request.scheduledAt().get());
            analyticsService.recordScheduled();
            notificationRepository.save(notification);
            return toReceipt(notification);
        }

        publishToKafka(notification);
        return toReceipt(notification);
    }

    public int drainKafkaToPriorityQueue(int maxMessages) {
        int drained = 0;
        while (drained < maxMessages) {
            Optional<BrokerMessage> message = messageBroker.poll(config.kafkaTopic(), CONSUMER_GROUP);
            if (message.isEmpty()) {
                break;
            }
            BrokerMessage brokerMessage = message.get();
            Notification notification = notificationRepository.findById(brokerMessage.payload())
                    .orElseThrow(() -> new ValidationException(
                            "Kafka payload references unknown notification: " + brokerMessage.payload()));
            priorityQueue.enqueue(notification);
            messageBroker.commit(config.kafkaTopic(), CONSUMER_GROUP, brokerMessage.offset());
            drained++;
        }
        return drained;
    }

    public int releaseDueScheduled() {
        List<Notification> due = scheduler.dueNotifications();
        for (Notification notification : due) {
            publishToKafka(notification);
        }
        return due.size();
    }

    public int releaseDueRetries() {
        Instant now = clock.instant();
        List<Notification> released = new ArrayList<>();
        retryLock.lock();
        try {
            while (!retryHeap.isEmpty() && !retryHeap.peek().readyAt().isAfter(now)) {
                released.add(retryHeap.poll().notification());
            }
        } finally {
            retryLock.unlock();
        }
        for (Notification notification : released) {
            priorityQueue.enqueue(notification);
        }
        return released.size();
    }

    public Optional<Notification> processNext() {
        releaseDueRetries();
        Optional<Notification> polled = priorityQueue.poll();
        if (polled.isEmpty()) {
            return Optional.empty();
        }
        Notification notification = polled.get();
        ChannelSendResult result = deliveryService.deliver(notification);
        priorityQueue.acknowledge(notification.id());

        if (result.success()) {
            analyticsService.recordDelivered(notification.channel());
            return Optional.of(notification);
        }

        analyticsService.recordFailed();
        if (retryPolicy.shouldRetry(notification.attemptCount())) {
            Duration delay = retryPolicy.nextDelay(notification.attemptCount());
            notification.markRetrying(result.errorMessage());
            notificationRepository.save(notification);
            analyticsService.recordRetry();
            scheduleRetry(notification, clock.instant().plus(delay));
        } else {
            deadLetterQueue.enqueue(notification, result.errorMessage());
            messageBroker.publish(config.dlqTopic(), notification.id(), notification.id());
            analyticsService.recordDeadLetter();
            notificationRepository.save(notification);
        }
        return Optional.of(notification);
    }

    public int processAvailable(int max) {
        int processed = 0;
        while (processed < max) {
            if (processNext().isEmpty()) {
                break;
            }
            processed++;
        }
        return processed;
    }

    public Optional<Notification> findById(String id) {
        return notificationRepository.findById(id);
    }

    public DeadLetterQueue deadLetterQueue() {
        return deadLetterQueue;
    }

    public AnalyticsService analytics() {
        return analyticsService;
    }

    public PriorityNotificationQueue priorityQueue() {
        return priorityQueue;
    }

    public NotificationScheduler scheduler() {
        return scheduler;
    }

    public MessageBroker messageBroker() {
        return messageBroker;
    }

    public DeliveryService deliveryService() {
        return deliveryService;
    }

    public int pendingRetries() {
        retryLock.lock();
        try {
            return retryHeap.size();
        } finally {
            retryLock.unlock();
        }
    }

    private void scheduleRetry(Notification notification, Instant readyAt) {
        retryLock.lock();
        try {
            retryHeap.offer(new DelayedRetry(notification, readyAt));
        } finally {
            retryLock.unlock();
        }
    }

    private void publishToKafka(Notification notification) {
        messageBroker.publish(config.kafkaTopic(), notification.id(), notification.id());
        notification.markQueued();
        notificationRepository.save(notification);
    }

    private void enforceRateLimit(SendNotificationRequest request) {
        String key = "rate:" + request.channel() + ":" + request.recipient().trim();
        long count = cacheStore.increment(key, RATE_LIMIT_WINDOW);
        if (count > rateLimitPerRecipient) {
            throw new ValidationException(
                    "Rate limit exceeded for " + request.recipient() + " on " + request.channel()
                            + " (" + count + "/" + rateLimitPerRecipient + " per minute)");
        }
    }

    private NotificationReceipt toReceipt(Notification notification) {
        return new NotificationReceipt(
                notification.id(),
                notification.channel(),
                notification.recipient(),
                notification.priority(),
                notification.status(),
                notification.createdAt(),
                notification.scheduledAt().orElse(null));
    }

    private record DelayedRetry(Notification notification, Instant readyAt) {
    }
}
