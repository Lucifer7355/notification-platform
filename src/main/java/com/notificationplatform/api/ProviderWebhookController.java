package com.notificationplatform.api;

import com.notificationplatform.channel.HttpWebhookChannelSender;
import com.notificationplatform.domain.ChannelType;
import com.notificationplatform.persistence.ProviderInboxEntity;
import com.notificationplatform.persistence.ProviderInboxJpaRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/providers")
public class ProviderWebhookController {

    private final ProviderInboxJpaRepository inboxRepository;

    public ProviderWebhookController(ProviderInboxJpaRepository inboxRepository) {
        this.inboxRepository = inboxRepository;
    }

    @PostMapping("/sms")
    @Transactional
    public HttpWebhookChannelSender.ProviderAck sms(@RequestBody Map<String, Object> payload) {
        return accept(ChannelType.SMS, payload);
    }

    @PostMapping("/whatsapp")
    @Transactional
    public HttpWebhookChannelSender.ProviderAck whatsapp(@RequestBody Map<String, Object> payload) {
        return accept(ChannelType.WHATSAPP, payload);
    }

    @PostMapping("/push")
    @Transactional
    public HttpWebhookChannelSender.ProviderAck push(@RequestBody Map<String, Object> payload) {
        return accept(ChannelType.PUSH, payload);
    }

    @PostMapping("/slack")
    @Transactional
    public HttpWebhookChannelSender.ProviderAck slack(@RequestBody Map<String, Object> payload) {
        return accept(ChannelType.SLACK, payload);
    }

    @PostMapping("/{channel}")
    @Transactional
    public HttpWebhookChannelSender.ProviderAck generic(
            @PathVariable ChannelType channel,
            @RequestBody Map<String, Object> payload) {
        return accept(channel, payload);
    }

    private HttpWebhookChannelSender.ProviderAck accept(ChannelType channel, Map<String, Object> payload) {
        ProviderInboxEntity entity = new ProviderInboxEntity();
        entity.setChannel(channel);
        entity.setRecipient(String.valueOf(payload.getOrDefault("recipient", "")));
        entity.setSubject(payload.get("subject") == null ? null : String.valueOf(payload.get("subject")));
        entity.setBody(String.valueOf(payload.getOrDefault("body", "")));
        Object notificationId = payload.get("notificationId");
        entity.setNotificationId(notificationId == null ? null : String.valueOf(notificationId));
        entity.setReceivedAt(Instant.now());
        inboxRepository.save(entity);
        return new HttpWebhookChannelSender.ProviderAck(channel.name().toLowerCase() + "-" + UUID.randomUUID());
    }
}
