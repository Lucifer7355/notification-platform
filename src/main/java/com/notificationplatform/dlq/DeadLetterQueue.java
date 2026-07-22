package com.notificationplatform.dlq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificationplatform.domain.DeadLetterRecord;
import com.notificationplatform.domain.Notification;
import com.notificationplatform.exception.NotificationException;
import com.notificationplatform.persistence.DeadLetterEntity;
import com.notificationplatform.persistence.DeadLetterJpaRepository;
import com.notificationplatform.persistence.NotificationMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DeadLetterQueue {

    private final DeadLetterJpaRepository repository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public DeadLetterQueue(DeadLetterJpaRepository repository, Clock clock, ObjectMapper objectMapper) {
        this.repository = repository;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DeadLetterRecord enqueue(Notification notification, String reason) {
        notification.markDeadLettered(reason);
        DeadLetterEntity entity = new DeadLetterEntity();
        entity.setNotificationId(notification.id());
        entity.setReason(reason);
        entity.setTotalAttempts(notification.attemptCount());
        entity.setDeadLetteredAt(clock.instant());
        entity.setPayloadJson(toPayload(notification));
        repository.save(entity);
        return new DeadLetterRecord(
                notification.id(),
                notification.copyForReplay(),
                reason,
                entity.getDeadLetteredAt(),
                notification.attemptCount());
    }

    @Transactional(readOnly = true)
    public List<DeadLetterRecord> snapshot() {
        return repository.findAllByOrderByDeadLetteredAtDesc().stream()
                .map(entity -> new DeadLetterRecord(
                        entity.getNotificationId(),
                        Notification.builder()
                                .id(entity.getNotificationId())
                                .channel(com.notificationplatform.domain.ChannelType.EMAIL)
                                .recipient("n/a")
                                .templateId("n/a")
                                .createdAt(entity.getDeadLetteredAt())
                                .subject("dead-letter")
                                .renderedBody(entity.getPayloadJson())
                                .status(com.notificationplatform.domain.NotificationStatus.DEAD_LETTERED)
                                .attemptCount(entity.getTotalAttempts())
                                .lastError(entity.getReason())
                                .build(),
                        entity.getReason(),
                        entity.getDeadLetteredAt(),
                        entity.getTotalAttempts()))
                .toList();
    }

    @Transactional(readOnly = true)
    public int size() {
        return (int) repository.count();
    }

    @Transactional
    public Optional<DeadLetterRecord> poll() {
        List<DeadLetterEntity> all = repository.findAllByOrderByDeadLetteredAtDesc();
        if (all.isEmpty()) {
            return Optional.empty();
        }
        DeadLetterEntity entity = all.get(all.size() - 1);
        repository.delete(entity);
        return Optional.of(new DeadLetterRecord(
                entity.getNotificationId(),
                Notification.builder()
                        .id(entity.getNotificationId())
                        .channel(com.notificationplatform.domain.ChannelType.EMAIL)
                        .recipient("n/a")
                        .templateId("n/a")
                        .createdAt(entity.getDeadLetteredAt())
                        .subject("dead-letter")
                        .renderedBody(entity.getPayloadJson())
                        .status(com.notificationplatform.domain.NotificationStatus.DEAD_LETTERED)
                        .attemptCount(entity.getTotalAttempts())
                        .lastError(entity.getReason())
                        .build(),
                entity.getReason(),
                entity.getDeadLetteredAt(),
                entity.getTotalAttempts()));
    }

    private String toPayload(Notification notification) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("id", notification.id());
            payload.put("channel", notification.channel().name());
            payload.put("recipient", notification.recipient());
            payload.put("templateId", notification.templateId());
            payload.put("variables", notification.variables());
            payload.put("priority", notification.priority().name());
            payload.put("subject", notification.subject());
            payload.put("body", notification.renderedBody());
            payload.put("variablesJson", NotificationMapper.toJson(notification.variables()));
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new NotificationException("Failed to serialize dead letter payload", ex);
        }
    }
}
