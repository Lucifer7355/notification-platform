package com.notificationplatform.service;

import com.notificationplatform.analytics.AnalyticsService;
import com.notificationplatform.channel.ChannelSendResult;
import com.notificationplatform.config.PlatformProperties;
import com.notificationplatform.dlq.DeadLetterQueue;
import com.notificationplatform.domain.Notification;
import com.notificationplatform.domain.NotificationStatus;
import com.notificationplatform.dto.NotificationReceipt;
import com.notificationplatform.dto.SendNotificationRequest;
import com.notificationplatform.exception.ValidationException;
import com.notificationplatform.kafka.MessageBroker;
import com.notificationplatform.queue.PriorityNotificationQueue;
import com.notificationplatform.redis.CacheStore;
import com.notificationplatform.repository.NotificationRepository;
import com.notificationplatform.retry.RetryPolicy;
import com.notificationplatform.template.TemplateRenderer;
import com.notificationplatform.util.IdGenerator;
import com.notificationplatform.validation.NotificationRequestValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class NotificationPlatformService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);

    private final PlatformProperties properties;
    private final NotificationRequestValidator validator;
    private final TemplateService templateService;
    private final NotificationRepository notificationRepository;
    private final MessageBroker messageBroker;
    private final PriorityNotificationQueue priorityQueue;
    private final DeliveryService deliveryService;
    private final RetryPolicy retryPolicy;
    private final DeadLetterQueue deadLetterQueue;
    private final AnalyticsService analyticsService;
    private final CacheStore cacheStore;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public NotificationPlatformService(
            PlatformProperties properties,
            NotificationRequestValidator validator,
            TemplateService templateService,
            NotificationRepository notificationRepository,
            MessageBroker messageBroker,
            PriorityNotificationQueue priorityQueue,
            DeliveryService deliveryService,
            RetryPolicy retryPolicy,
            DeadLetterQueue deadLetterQueue,
            AnalyticsService analyticsService,
            CacheStore cacheStore,
            IdGenerator idGenerator,
            Clock clock) {
        this.properties = properties;
        this.validator = validator;
        this.templateService = templateService;
        this.notificationRepository = notificationRepository;
        this.messageBroker = messageBroker;
        this.priorityQueue = priorityQueue;
        this.deliveryService = deliveryService;
        this.retryPolicy = retryPolicy;
        this.deadLetterQueue = deadLetterQueue;
        this.analyticsService = analyticsService;
        this.cacheStore = cacheStore;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public NotificationReceipt accept(SendNotificationRequest request) {
        return accept(request, null);
    }

    @Transactional
    public NotificationReceipt accept(SendNotificationRequest request, String idempotencyKey) {
        validator.validate(request);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<String> existing = cacheStore.get("idempotency:" + idempotencyKey);
            if (existing.isPresent()) {
                return notificationRepository.findById(existing.get())
                        .map(this::toReceipt)
                        .orElseThrow(() -> new ValidationException("Idempotency key points to missing notification"));
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
                return cacheStore.get("idempotency:" + idempotencyKey)
                        .flatMap(notificationRepository::findById)
                        .map(this::toReceipt)
                        .orElseGet(() -> toReceipt(notification));
            }
        }

        if (request.scheduledAt().isPresent() && request.scheduledAt().get().isAfter(now)) {
            notification.markScheduled();
            notificationRepository.save(notification);
            analyticsService.recordScheduled();
            return toReceipt(notification);
        }

        publishToKafka(notification);
        return toReceipt(notification);
    }

    public void enqueueFromKafka(String notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ValidationException("Kafka payload references unknown notification: " + notificationId));
        priorityQueue.enqueue(notification);
    }

    @Transactional
    public int releaseDueScheduled() {
        List<Notification> due = notificationRepository.findDueScheduled(clock.instant());
        for (Notification notification : due) {
            publishToKafka(notification);
        }
        return due.size();
    }

    @Transactional
    public int releaseDueRetries() {
        List<Notification> due = notificationRepository.findDueRetries(clock.instant());
        for (Notification notification : due) {
            priorityQueue.enqueue(notification);
        }
        return due.size();
    }

    @Transactional
    public Optional<Notification> processNext() {
        Optional<Notification> polled = priorityQueue.poll();
        if (polled.isEmpty()) {
            return Optional.empty();
        }
        Notification queued = polled.get();
        Notification notification = notificationRepository.findById(queued.id()).orElse(queued);

        ChannelSendResult result = deliveryService.deliver(notification);
        priorityQueue.acknowledge(notification.id());

        if (result.success()) {
            analyticsService.recordDelivered(notification.channel());
            return notificationRepository.findById(notification.id());
        }

        analyticsService.recordFailed();
        Notification latest = notificationRepository.findById(notification.id()).orElse(notification);
        if (retryPolicy.shouldRetry(latest.attemptCount())) {
            Duration delay = retryPolicy.nextDelay(latest.attemptCount());
            Instant retryAt = clock.instant().plus(delay);
            latest.markRetrying(result.errorMessage(), retryAt);
            notificationRepository.save(latest);
            analyticsService.recordRetry();
        } else {
            deadLetterQueue.enqueue(latest, result.errorMessage());
            messageBroker.publish(properties.dlqTopic(), latest.id(), latest.id());
            analyticsService.recordDeadLetter();
            notificationRepository.save(latest);
        }
        return notificationRepository.findById(notification.id());
    }

    @Transactional
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

    public MessageBroker messageBroker() {
        return messageBroker;
    }

    public DeliveryService deliveryService() {
        return deliveryService;
    }

    private void publishToKafka(Notification notification) {
        messageBroker.publish(properties.kafkaTopic(), notification.id(), notification.id());
        notification.markQueued();
        notificationRepository.save(notification);
    }

    private void enforceRateLimit(SendNotificationRequest request) {
        String key = "rate:" + request.channel() + ":" + request.recipient().trim();
        long count = cacheStore.increment(key, RATE_LIMIT_WINDOW);
        if (count > properties.rateLimitPerRecipient()) {
            throw new ValidationException(
                    "Rate limit exceeded for " + request.recipient() + " on " + request.channel()
                            + " (" + count + "/" + properties.rateLimitPerRecipient() + " per minute)");
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
}
