package com.notificationplatform.channel;

import com.notificationplatform.domain.ChannelType;
import com.notificationplatform.domain.Notification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

public abstract class HttpWebhookChannelSender implements ChannelSender {

    private final ChannelType channelType;
    private final String webhookUrl;
    private final RestClient restClient;

    protected HttpWebhookChannelSender(ChannelType channelType, String webhookUrl, RestClient.Builder builder) {
        this.channelType = channelType;
        this.webhookUrl = webhookUrl;
        this.restClient = builder.build();
    }

    @Override
    public ChannelType channel() {
        return channelType;
    }

    @Override
    public ChannelSendResult send(Notification notification) {
        try {
            Map<String, Object> body = Map.of(
                    "notificationId", notification.id(),
                    "channel", notification.channel().name(),
                    "recipient", notification.recipient(),
                    "subject", notification.subject(),
                    "body", notification.renderedBody(),
                    "priority", notification.priority().name());

            ProviderAck ack = restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(ProviderAck.class);

            if (ack == null || ack.providerMessageId() == null || ack.providerMessageId().isBlank()) {
                return ChannelSendResult.failure(channelType + " provider returned empty ack");
            }
            return ChannelSendResult.success(ack.providerMessageId());
        } catch (Exception ex) {
            return ChannelSendResult.failure(channelType + " webhook failed: " + ex.getMessage());
        }
    }

    public record ProviderAck(String providerMessageId) {
    }

    @Component
    public static class SmsSender extends HttpWebhookChannelSender {
        public SmsSender(
                RestClient.Builder builder,
                @Value("${platform.provider.sms-webhook-url}") String url) {
            super(ChannelType.SMS, url, builder);
        }
    }

    @Component
    public static class WhatsAppSender extends HttpWebhookChannelSender {
        public WhatsAppSender(
                RestClient.Builder builder,
                @Value("${platform.provider.whatsapp-webhook-url}") String url) {
            super(ChannelType.WHATSAPP, url, builder);
        }
    }

    @Component
    public static class PushSender extends HttpWebhookChannelSender {
        public PushSender(
                RestClient.Builder builder,
                @Value("${platform.provider.push-webhook-url}") String url) {
            super(ChannelType.PUSH, url, builder);
        }
    }

    @Component
    public static class SlackSender extends HttpWebhookChannelSender {
        public SlackSender(
                RestClient.Builder builder,
                @Value("${platform.provider.slack-webhook-url}") String url) {
            super(ChannelType.SLACK, url, builder);
        }
    }
}
