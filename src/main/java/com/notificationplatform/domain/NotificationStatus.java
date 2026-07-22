package com.notificationplatform.domain;

public enum NotificationStatus {
    ACCEPTED,
    QUEUED,
    SCHEDULED,
    SENDING,
    DELIVERED,
    FAILED,
    RETRYING,
    DEAD_LETTERED
}
