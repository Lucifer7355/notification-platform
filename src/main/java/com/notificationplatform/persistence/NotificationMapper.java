package com.notificationplatform.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificationplatform.domain.Notification;
import com.notificationplatform.exception.NotificationException;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NotificationMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, String>> MAP_TYPE = new TypeReference<>() {
    };

    private NotificationMapper() {
    }

    public static NotificationEntity toEntity(Notification notification) {
        NotificationEntity entity = new NotificationEntity();
        entity.setId(notification.id());
        entity.setChannel(notification.channel());
        entity.setRecipient(notification.recipient());
        entity.setTemplateId(notification.templateId());
        entity.setVariablesJson(toJson(notification.variables()));
        entity.setPriority(notification.priority());
        entity.setStatus(notification.status());
        entity.setSubject(notification.subject());
        entity.setRenderedBody(notification.renderedBody());
        entity.setAttemptCount(notification.attemptCount());
        entity.setLastError(notification.lastError().orElse(null));
        entity.setCreatedAt(notification.createdAt());
        entity.setScheduledAt(notification.scheduledAt().orElse(null));
        entity.setLastAttemptAt(notification.lastAttemptAt().orElse(null));
        entity.setDeliveredAt(notification.deliveredAt().orElse(null));
        entity.setNextRetryAt(notification.nextRetryAt().orElse(null));
        return entity;
    }

    public static void copyToEntity(Notification notification, NotificationEntity entity) {
        entity.setChannel(notification.channel());
        entity.setRecipient(notification.recipient());
        entity.setTemplateId(notification.templateId());
        entity.setVariablesJson(toJson(notification.variables()));
        entity.setPriority(notification.priority());
        entity.setStatus(notification.status());
        entity.setSubject(notification.subject());
        entity.setRenderedBody(notification.renderedBody());
        entity.setAttemptCount(notification.attemptCount());
        entity.setLastError(notification.lastError().orElse(null));
        entity.setCreatedAt(notification.createdAt());
        entity.setScheduledAt(notification.scheduledAt().orElse(null));
        entity.setLastAttemptAt(notification.lastAttemptAt().orElse(null));
        entity.setDeliveredAt(notification.deliveredAt().orElse(null));
        entity.setNextRetryAt(notification.nextRetryAt().orElse(null));
    }

    public static Notification toDomain(NotificationEntity entity) {
        return Notification.builder()
                .id(entity.getId())
                .channel(entity.getChannel())
                .recipient(entity.getRecipient())
                .templateId(entity.getTemplateId())
                .variables(fromJson(entity.getVariablesJson()))
                .priority(entity.getPriority())
                .status(entity.getStatus())
                .subject(entity.getSubject())
                .renderedBody(entity.getRenderedBody())
                .attemptCount(entity.getAttemptCount())
                .lastError(entity.getLastError())
                .createdAt(entity.getCreatedAt())
                .scheduledAt(entity.getScheduledAt())
                .lastAttemptAt(entity.getLastAttemptAt())
                .deliveredAt(entity.getDeliveredAt())
                .nextRetryAt(entity.getNextRetryAt())
                .build();
    }

    public static String toJson(Map<String, String> variables) {
        try {
            return MAPPER.writeValueAsString(variables == null ? Map.of() : variables);
        } catch (JsonProcessingException ex) {
            throw new NotificationException("Failed to serialize variables", ex);
        }
    }

    public static Map<String, String> fromJson(String json) {
        try {
            if (json == null || json.isBlank()) {
                return Map.of();
            }
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new NotificationException("Failed to deserialize variables", ex);
        }
    }
}
