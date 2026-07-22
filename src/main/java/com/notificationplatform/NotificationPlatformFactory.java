package com.notificationplatform;

import com.notificationplatform.analytics.AnalyticsService;
import com.notificationplatform.channel.ChannelSender;
import com.notificationplatform.channel.ChannelSenderRegistry;
import com.notificationplatform.channel.ConfigurableChannelSender;
import com.notificationplatform.config.PlatformConfig;
import com.notificationplatform.dlq.DeadLetterQueue;
import com.notificationplatform.domain.ChannelType;
import com.notificationplatform.domain.NotificationTemplate;
import com.notificationplatform.kafka.InMemoryMessageBroker;
import com.notificationplatform.kafka.MessageBroker;
import com.notificationplatform.queue.PriorityNotificationQueue;
import com.notificationplatform.redis.CacheStore;
import com.notificationplatform.redis.InMemoryCacheStore;
import com.notificationplatform.repository.InMemoryNotificationRepository;
import com.notificationplatform.repository.InMemoryTemplateRepository;
import com.notificationplatform.repository.NotificationRepository;
import com.notificationplatform.repository.TemplateRepository;
import com.notificationplatform.retry.ExponentialBackoffRetryPolicy;
import com.notificationplatform.retry.RetryPolicy;
import com.notificationplatform.scheduling.NotificationScheduler;
import com.notificationplatform.service.DeliveryService;
import com.notificationplatform.service.NotificationPlatformService;
import com.notificationplatform.service.TemplateService;
import com.notificationplatform.template.TemplateRenderer;
import com.notificationplatform.util.IdGenerator;
import com.notificationplatform.validation.NotificationRequestValidator;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class NotificationPlatformFactory {

    private NotificationPlatformFactory() {
    }

    public static NotificationPlatformService createDefault() {
        return create(Clock.systemUTC(), PlatformConfig.defaults(), notification -> false, 1000, IdGenerator.sequential("ntf"));
    }

    public static NotificationPlatformService create(
            Clock clock,
            PlatformConfig config,
            Predicate<com.notificationplatform.domain.Notification> failurePredicate,
            long rateLimitPerRecipient,
            IdGenerator idGenerator) {

        NotificationRepository notificationRepository = new InMemoryNotificationRepository();
        TemplateRepository templateRepository = new InMemoryTemplateRepository();
        CacheStore cacheStore = new InMemoryCacheStore(clock);
        MessageBroker messageBroker = new InMemoryMessageBroker(clock);
        PriorityNotificationQueue priorityQueue = new PriorityNotificationQueue(config.visibilityTimeout(), clock);
        NotificationScheduler scheduler = new NotificationScheduler(clock);
        DeadLetterQueue deadLetterQueue = new DeadLetterQueue(clock);
        AnalyticsService analyticsService = new AnalyticsService();
        RetryPolicy retryPolicy = new ExponentialBackoffRetryPolicy(config);
        TemplateService templateService = new TemplateService(templateRepository, new TemplateRenderer(), cacheStore);

        List<ChannelSender> senders = List.of(
                new ConfigurableChannelSender(ChannelType.EMAIL, failurePredicate),
                new ConfigurableChannelSender(ChannelType.SMS, failurePredicate),
                new ConfigurableChannelSender(ChannelType.WHATSAPP, failurePredicate),
                new ConfigurableChannelSender(ChannelType.PUSH, failurePredicate),
                new ConfigurableChannelSender(ChannelType.SLACK, failurePredicate));
        ChannelSenderRegistry registry = new ChannelSenderRegistry(senders);
        DeliveryService deliveryService = new DeliveryService(registry, notificationRepository, clock);

        seedTemplates(templateService);

        return new NotificationPlatformService(
                config,
                new NotificationRequestValidator(),
                templateService,
                notificationRepository,
                messageBroker,
                priorityQueue,
                scheduler,
                deliveryService,
                retryPolicy,
                deadLetterQueue,
                analyticsService,
                cacheStore,
                idGenerator,
                clock,
                rateLimitPerRecipient);
    }

    public static void seedTemplates(TemplateService templateService) {
        templateService.register(new NotificationTemplate(
                "email-welcome",
                ChannelType.EMAIL,
                "Welcome Email",
                "Welcome {{name}}",
                "Hi {{name}}, welcome to {{product}}!",
                Set.of("name", "product")));
        templateService.register(new NotificationTemplate(
                "sms-otp",
                ChannelType.SMS,
                "OTP SMS",
                "OTP",
                "Your OTP is {{otp}}. Valid for {{minutes}} minutes.",
                Set.of("otp", "minutes")));
        templateService.register(new NotificationTemplate(
                "wa-order",
                ChannelType.WHATSAPP,
                "Order WhatsApp",
                "Order Update",
                "Hi {{name}}, order {{orderId}} is {{status}}.",
                Set.of("name", "orderId", "status")));
        templateService.register(new NotificationTemplate(
                "push-alert",
                ChannelType.PUSH,
                "Push Alert",
                "{{title}}",
                "{{body}}",
                Set.of("title", "body")));
        templateService.register(new NotificationTemplate(
                "slack-deploy",
                ChannelType.SLACK,
                "Deploy Slack",
                "Deploy {{env}}",
                "Service {{service}} deployed to {{env}} by {{actor}}.",
                Set.of("service", "env", "actor")));
    }
}
