package com.notificationplatform.analytics;

import com.notificationplatform.domain.ChannelType;
import com.notificationplatform.domain.NotificationStatus;
import com.notificationplatform.domain.Priority;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public final class AnalyticsService {

    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong delivered = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong retried = new AtomicLong();
    private final AtomicLong deadLettered = new AtomicLong();
    private final AtomicLong scheduled = new AtomicLong();
    private final ConcurrentHashMap<ChannelType, AtomicLong> deliveredByChannel = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Priority, AtomicLong> acceptedByPriority = new ConcurrentHashMap<>();

    public void recordAccepted(ChannelType channel, Priority priority) {
        accepted.incrementAndGet();
        acceptedByPriority.computeIfAbsent(priority, ignored -> new AtomicLong()).incrementAndGet();
        deliveredByChannel.computeIfAbsent(channel, ignored -> new AtomicLong());
    }

    public void recordScheduled() {
        scheduled.incrementAndGet();
    }

    public void recordDelivered(ChannelType channel) {
        delivered.incrementAndGet();
        deliveredByChannel.computeIfAbsent(channel, ignored -> new AtomicLong()).incrementAndGet();
    }

    public void recordFailed() {
        failed.incrementAndGet();
    }

    public void recordRetry() {
        retried.incrementAndGet();
    }

    public void recordDeadLetter() {
        deadLettered.incrementAndGet();
    }

    public void recordStatus(NotificationStatus status, ChannelType channel) {
        switch (status) {
            case DELIVERED -> recordDelivered(channel);
            case FAILED -> recordFailed();
            case RETRYING -> recordRetry();
            case DEAD_LETTERED -> recordDeadLetter();
            case SCHEDULED -> recordScheduled();
            default -> {
                // ACCEPTED / QUEUED / SENDING tracked elsewhere
            }
        }
    }

    public AnalyticsSnapshot snapshot() {
        Map<ChannelType, Long> channelCounts = new EnumMap<>(ChannelType.class);
        deliveredByChannel.forEach((channel, count) -> channelCounts.put(channel, count.get()));
        Map<Priority, Long> priorityCounts = new EnumMap<>(Priority.class);
        acceptedByPriority.forEach((priority, count) -> priorityCounts.put(priority, count.get()));
        return new AnalyticsSnapshot(
                accepted.get(),
                delivered.get(),
                failed.get(),
                retried.get(),
                deadLettered.get(),
                scheduled.get(),
                Map.copyOf(channelCounts),
                Map.copyOf(priorityCounts));
    }

    public String renderReport() {
        AnalyticsSnapshot snap = snapshot();
        String channels = snap.deliveredByChannel().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
        String priorities = snap.acceptedByPriority().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
        return """
                accepted=%d delivered=%d failed=%d retried=%d deadLettered=%d scheduled=%d
                byChannel={%s}
                byPriority={%s}
                """.formatted(
                snap.accepted(),
                snap.delivered(),
                snap.failed(),
                snap.retried(),
                snap.deadLettered(),
                snap.scheduled(),
                channels,
                priorities).trim();
    }
}
