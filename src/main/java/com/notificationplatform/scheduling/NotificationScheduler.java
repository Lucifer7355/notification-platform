package com.notificationplatform.scheduling;

import com.notificationplatform.domain.Notification;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.locks.ReentrantLock;

public final class NotificationScheduler {

    private final PriorityQueue<ScheduledNotification> heap;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    public NotificationScheduler(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.heap = new PriorityQueue<>(Comparator.comparing(ScheduledNotification::fireAt));
    }

    public void schedule(Notification notification, Instant fireAt) {
        Objects.requireNonNull(notification, "notification");
        Objects.requireNonNull(fireAt, "fireAt");
        notification.markScheduled();
        lock.lock();
        try {
            heap.offer(new ScheduledNotification(notification, fireAt));
        } finally {
            lock.unlock();
        }
    }

    public List<Notification> dueNotifications() {
        Instant now = clock.instant();
        List<Notification> due = new ArrayList<>();
        lock.lock();
        try {
            while (!heap.isEmpty() && !heap.peek().fireAt().isAfter(now)) {
                due.add(heap.poll().notification());
            }
        } finally {
            lock.unlock();
        }
        return due;
    }

    public int pendingCount() {
        lock.lock();
        try {
            return heap.size();
        } finally {
            lock.unlock();
        }
    }

    private record ScheduledNotification(Notification notification, Instant fireAt) {
    }
}
