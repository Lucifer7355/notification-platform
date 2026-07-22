package com.notificationplatform.channel;

import com.notificationplatform.domain.ChannelType;
import com.notificationplatform.domain.Notification;
import com.notificationplatform.exception.ChannelDeliveryException;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public final class ConfigurableChannelSender implements ChannelSender {

    private final ChannelType channel;
    private final Predicate<Notification> failurePredicate;
    private final AtomicInteger sendCount = new AtomicInteger();
    private final Set<String> deliveredIds = ConcurrentHashMap.newKeySet();

    public ConfigurableChannelSender(ChannelType channel) {
        this(channel, notification -> false);
    }

    public ConfigurableChannelSender(ChannelType channel, Predicate<Notification> failurePredicate) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.failurePredicate = Objects.requireNonNull(failurePredicate, "failurePredicate");
    }

    @Override
    public ChannelType channel() {
        return channel;
    }

    @Override
    public ChannelSendResult send(Notification notification) {
        Objects.requireNonNull(notification, "notification");
        if (notification.channel() != channel) {
            throw new ChannelDeliveryException(
                    "Channel mismatch: sender=" + channel + ", notification=" + notification.channel());
        }
        int attempt = sendCount.incrementAndGet();
        if (failurePredicate.test(notification)) {
            return ChannelSendResult.failure(channel + " provider rejected attempt #" + attempt
                    + " for " + notification.id());
        }
        String providerId = channel.name().toLowerCase() + "-" + notification.id() + "-" + attempt;
        deliveredIds.add(notification.id());
        return ChannelSendResult.success(providerId);
    }

    public int sendCount() {
        return sendCount.get();
    }

    public boolean wasDelivered(String notificationId) {
        return deliveredIds.contains(notificationId);
    }
}
