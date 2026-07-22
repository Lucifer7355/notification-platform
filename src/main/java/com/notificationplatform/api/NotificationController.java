package com.notificationplatform.api;

import com.notificationplatform.domain.ChannelType;
import com.notificationplatform.domain.Notification;
import com.notificationplatform.domain.Priority;
import com.notificationplatform.dto.NotificationReceipt;
import com.notificationplatform.dto.SendNotificationRequest;
import com.notificationplatform.persistence.DeadLetterEntity;
import com.notificationplatform.persistence.DeadLetterJpaRepository;
import com.notificationplatform.persistence.ProviderInboxEntity;
import com.notificationplatform.persistence.ProviderInboxJpaRepository;
import com.notificationplatform.repository.TemplateRepository;
import com.notificationplatform.service.DeliveryService;
import com.notificationplatform.service.NotificationPlatformService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {

    private final NotificationPlatformService platformService;
    private final DeliveryService deliveryService;
    private final TemplateRepository templateRepository;
    private final DeadLetterJpaRepository deadLetterJpaRepository;
    private final ProviderInboxJpaRepository providerInboxJpaRepository;

    public NotificationController(
            NotificationPlatformService platformService,
            DeliveryService deliveryService,
            TemplateRepository templateRepository,
            DeadLetterJpaRepository deadLetterJpaRepository,
            ProviderInboxJpaRepository providerInboxJpaRepository) {
        this.platformService = platformService;
        this.deliveryService = deliveryService;
        this.templateRepository = templateRepository;
        this.deadLetterJpaRepository = deadLetterJpaRepository;
        this.providerInboxJpaRepository = providerInboxJpaRepository;
    }

    @PostMapping("/notifications")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public NotificationReceipt send(
            @Valid @RequestBody SendRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        SendNotificationRequest request = SendNotificationRequest.builder()
                .channel(body.channel())
                .recipient(body.recipient())
                .templateId(body.templateId())
                .variables(body.variables() == null ? Map.of() : body.variables())
                .priority(body.priority() == null ? Priority.NORMAL : body.priority())
                .scheduledAt(body.scheduledAt())
                .build();
        return platformService.accept(request, idempotencyKey);
    }

    @GetMapping("/notifications/{id}")
    public NotificationView get(@PathVariable String id) {
        Notification notification = platformService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        return NotificationView.from(notification);
    }

    @GetMapping("/notifications/{id}/attempts")
    public Object attempts(@PathVariable String id) {
        return deliveryService.attemptsFor(id);
    }

    @GetMapping("/templates")
    public Object templates() {
        return templateRepository.findAll().stream()
                .map(t -> Map.of(
                        "id", t.id(),
                        "channel", t.channel().name(),
                        "name", t.name(),
                        "requiredVariables", t.requiredVariables()))
                .toList();
    }

    @GetMapping("/analytics")
    public Object analytics() {
        return platformService.analytics().snapshot();
    }

    @GetMapping("/dead-letters")
    public List<DeadLetterEntity> deadLetters() {
        return deadLetterJpaRepository.findAllByOrderByDeadLetteredAtDesc();
    }

    @GetMapping("/provider-inbox")
    public List<ProviderInboxEntity> providerInbox() {
        return providerInboxJpaRepository.findAll();
    }

    @GetMapping("/provider-inbox/{channel}")
    public List<ProviderInboxEntity> providerInboxByChannel(@PathVariable ChannelType channel) {
        return providerInboxJpaRepository.findByChannelOrderByReceivedAtDesc(channel);
    }

    public record SendRequest(
            @NotNull ChannelType channel,
            @NotBlank String recipient,
            @NotBlank String templateId,
            Map<String, String> variables,
            Priority priority,
            Instant scheduledAt
    ) {
    }

    public record NotificationView(
            String id,
            ChannelType channel,
            String recipient,
            String templateId,
            Priority priority,
            String status,
            String subject,
            String body,
            int attemptCount,
            String lastError,
            Instant createdAt,
            Instant scheduledAt,
            Instant deliveredAt,
            Instant nextRetryAt
    ) {
        static NotificationView from(Notification n) {
            return new NotificationView(
                    n.id(),
                    n.channel(),
                    n.recipient(),
                    n.templateId(),
                    n.priority(),
                    n.status().name(),
                    n.subject(),
                    n.renderedBody(),
                    n.attemptCount(),
                    n.lastError().orElse(null),
                    n.createdAt(),
                    n.scheduledAt().orElse(null),
                    n.deliveredAt().orElse(null),
                    n.nextRetryAt().orElse(null));
        }
    }
}
