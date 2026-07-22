package com.notificationplatform.dlq;

import com.notificationplatform.domain.DeadLetterRecord;
import com.notificationplatform.domain.Notification;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class DeadLetterQueue {

    private final ConcurrentLinkedQueue<DeadLetterRecord> records = new ConcurrentLinkedQueue<>();
    private final Clock clock;

    public DeadLetterQueue(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public DeadLetterRecord enqueue(Notification notification, String reason) {
        Objects.requireNonNull(notification, "notification");
        Objects.requireNonNull(reason, "reason");
        notification.markDeadLettered(reason);
        DeadLetterRecord record = new DeadLetterRecord(
                notification.id(),
                notification.copyForReplay(),
                reason,
                clock.instant(),
                notification.attemptCount());
        records.add(record);
        return record;
    }

    public Optional<DeadLetterRecord> poll() {
        return Optional.ofNullable(records.poll());
    }

    public List<DeadLetterRecord> snapshot() {
        return new ArrayList<>(records);
    }

    public int size() {
        return records.size();
    }
}
