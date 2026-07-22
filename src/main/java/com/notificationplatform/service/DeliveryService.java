package com.notificationplatform.service;

import com.notificationplatform.channel.ChannelSendResult;
import com.notificationplatform.channel.ChannelSenderRegistry;
import com.notificationplatform.domain.DeliveryAttempt;
import com.notificationplatform.domain.Notification;
import com.notificationplatform.repository.NotificationRepository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class DeliveryService {

    private final ChannelSenderRegistry channelSenderRegistry;
    private final NotificationRepository notificationRepository;
    private final Clock clock;
    private final ConcurrentLinkedQueue<DeliveryAttempt> attempts = new ConcurrentLinkedQueue<>();

    public DeliveryService(
            ChannelSenderRegistry channelSenderRegistry,
            NotificationRepository notificationRepository,
            Clock clock) {
        this.channelSenderRegistry = Objects.requireNonNull(channelSenderRegistry, "channelSenderRegistry");
        this.notificationRepository = Objects.requireNonNull(notificationRepository, "notificationRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ChannelSendResult deliver(Notification notification) {
        Objects.requireNonNull(notification, "notification");
        notification.markSending(clock.instant());
        notificationRepository.save(notification);

        ChannelSendResult result = channelSenderRegistry.get(notification.channel()).send(notification);
        DeliveryAttempt attempt = new DeliveryAttempt(
                notification.id(),
                notification.attemptCount(),
                clock.instant(),
                result.success(),
                result.providerMessageId(),
                result.errorMessage());
        attempts.add(attempt);

        if (result.success()) {
            notification.markDelivered(clock.instant());
        } else {
            notification.markFailed(result.errorMessage());
        }
        notificationRepository.save(notification);
        return result;
    }

    public List<DeliveryAttempt> attemptsFor(String notificationId) {
        List<DeliveryAttempt> matched = new ArrayList<>();
        for (DeliveryAttempt attempt : attempts) {
            if (attempt.notificationId().equals(notificationId)) {
                matched.add(attempt);
            }
        }
        return matched;
    }
}
