package com.notificationplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "platform")
public record PlatformProperties(
        String kafkaTopic,
        String dlqTopic,
        int maxRetryAttempts,
        Duration initialBackoff,
        double backoffMultiplier,
        Duration maxBackoff,
        Duration visibilityTimeout,
        long rateLimitPerRecipient,
        int workerBatchSize,
        Duration schedulerFixedDelay,
        Duration retryFixedDelay,
        Provider provider,
        Mail mail
) {
    public record Provider(
            String smsWebhookUrl,
            String whatsappWebhookUrl,
            String pushWebhookUrl,
            String slackWebhookUrl
    ) {
    }

    public record Mail(String from) {
    }
}
