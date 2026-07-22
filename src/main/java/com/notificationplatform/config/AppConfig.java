package com.notificationplatform.config;

import com.notificationplatform.analytics.AnalyticsService;
import com.notificationplatform.channel.ChannelSender;
import com.notificationplatform.channel.ChannelSenderRegistry;
import com.notificationplatform.queue.PriorityNotificationQueue;
import com.notificationplatform.retry.ExponentialBackoffRetryPolicy;
import com.notificationplatform.retry.RetryPolicy;
import com.notificationplatform.template.TemplateRenderer;
import com.notificationplatform.util.IdGenerator;
import com.notificationplatform.validation.NotificationRequestValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.List;

@Configuration
@EnableScheduling
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    IdGenerator idGenerator() {
        return IdGenerator.sequential("ntf");
    }

    @Bean
    TemplateRenderer templateRenderer() {
        return new TemplateRenderer();
    }

    @Bean
    NotificationRequestValidator notificationRequestValidator() {
        return new NotificationRequestValidator();
    }

    @Bean
    AnalyticsService analyticsService() {
        return new AnalyticsService();
    }

    @Bean
    RetryPolicy retryPolicy(PlatformProperties properties) {
        return new ExponentialBackoffRetryPolicy(
                properties.maxRetryAttempts(),
                properties.initialBackoff(),
                properties.backoffMultiplier(),
                properties.maxBackoff());
    }

    @Bean
    PriorityNotificationQueue priorityNotificationQueue(PlatformProperties properties, Clock clock) {
        return new PriorityNotificationQueue(properties.visibilityTimeout(), clock);
    }

    @Bean
    ChannelSenderRegistry channelSenderRegistry(List<ChannelSender> senders) {
        return new ChannelSenderRegistry(senders);
    }

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
