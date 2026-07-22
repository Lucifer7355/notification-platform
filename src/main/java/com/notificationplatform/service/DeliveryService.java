package com.notificationplatform.service;

import com.notificationplatform.channel.ChannelSendResult;
import com.notificationplatform.channel.ChannelSenderRegistry;
import com.notificationplatform.domain.DeliveryAttempt;
import com.notificationplatform.domain.Notification;
import com.notificationplatform.persistence.DeliveryAttemptEntity;
import com.notificationplatform.persistence.DeliveryAttemptJpaRepository;
import com.notificationplatform.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class DeliveryService {

    private final ChannelSenderRegistry channelSenderRegistry;
    private final NotificationRepository notificationRepository;
    private final DeliveryAttemptJpaRepository attemptRepository;
    private final Clock clock;

    public DeliveryService(
            ChannelSenderRegistry channelSenderRegistry,
            NotificationRepository notificationRepository,
            DeliveryAttemptJpaRepository attemptRepository,
            Clock clock) {
        this.channelSenderRegistry = channelSenderRegistry;
        this.notificationRepository = notificationRepository;
        this.attemptRepository = attemptRepository;
        this.clock = clock;
    }

    @Transactional
    public ChannelSendResult deliver(Notification notification) {
        notification.markSending(clock.instant());
        notificationRepository.save(notification);

        ChannelSendResult result = channelSenderRegistry.get(notification.channel()).send(notification);

        DeliveryAttemptEntity attempt = new DeliveryAttemptEntity();
        attempt.setNotificationId(notification.id());
        attempt.setAttemptNumber(notification.attemptCount());
        attempt.setAttemptedAt(clock.instant());
        attempt.setSuccess(result.success());
        attempt.setProviderResponse(result.providerMessageId());
        attempt.setErrorMessage(result.errorMessage());
        attemptRepository.save(attempt);

        if (result.success()) {
            notification.markDelivered(clock.instant());
        } else {
            notification.markFailed(result.errorMessage());
        }
        notificationRepository.save(notification);
        return result;
    }

    @Transactional(readOnly = true)
    public List<DeliveryAttempt> attemptsFor(String notificationId) {
        return attemptRepository.findByNotificationIdOrderByAttemptNumberAsc(notificationId).stream()
                .map(entity -> new DeliveryAttempt(
                        entity.getNotificationId(),
                        entity.getAttemptNumber(),
                        entity.getAttemptedAt(),
                        entity.isSuccess(),
                        entity.getProviderResponse(),
                        entity.getErrorMessage()))
                .toList();
    }
}
