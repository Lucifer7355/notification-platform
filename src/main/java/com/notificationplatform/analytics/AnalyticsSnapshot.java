package com.notificationplatform.analytics;

import com.notificationplatform.domain.ChannelType;
import com.notificationplatform.domain.Priority;

import java.util.Map;

public record AnalyticsSnapshot(
        long accepted,
        long delivered,
        long failed,
        long retried,
        long deadLettered,
        long scheduled,
        Map<ChannelType, Long> deliveredByChannel,
        Map<Priority, Long> acceptedByPriority
) {
}
