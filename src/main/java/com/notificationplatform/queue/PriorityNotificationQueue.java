package com.notificationplatform.queue;

import com.notificationplatform.domain.Notification;
import com.notificationplatform.domain.Priority;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Priority queue with visibility timeout — in-flight messages reappear if not acked.
 */
public final class PriorityNotificationQueue {

    private final PriorityBlockingQueue<QueuedNotification> readyQueue;
    private final PriorityBlockingQueue<QueuedNotification> inFlightQueue;
    private final Duration visibilityTimeout;
    private final Clock clock;
    private final AtomicLong sequence = new AtomicLong();
    private final ReentrantLock reclaimLock = new ReentrantLock();

    public PriorityNotificationQueue(Duration visibilityTimeout, Clock clock) {
        this.visibilityTimeout = Objects.requireNonNull(visibilityTimeout, "visibilityTimeout");
        this.clock = Objects.requireNonNull(clock, "clock");
        Comparator<QueuedNotification> comparator = Comparator
                .comparingInt((QueuedNotification q) -> q.notification().priority().weight())
                .reversed()
                .thenComparingLong(QueuedNotification::sequence);
        this.readyQueue = new PriorityBlockingQueue<>(11, comparator);
        this.inFlightQueue = new PriorityBlockingQueue<>(11, Comparator.comparing(QueuedNotification::visibleAt));
    }

    public void enqueue(Notification notification) {
        Objects.requireNonNull(notification, "notification");
        notification.markQueued();
        readyQueue.offer(new QueuedNotification(notification, sequence.incrementAndGet(), Instant.MIN));
    }

    public Optional<Notification> poll() {
        reclaimExpired();
        QueuedNotification next = readyQueue.poll();
        if (next == null) {
            return Optional.empty();
        }
        Instant visibleAt = clock.instant().plus(visibilityTimeout);
        inFlightQueue.offer(new QueuedNotification(next.notification(), next.sequence(), visibleAt));
        return Optional.of(next.notification());
    }

    public boolean acknowledge(String notificationId) {
        reclaimLock.lock();
        try {
            List<QueuedNotification> remaining = new ArrayList<>();
            boolean removed = false;
            QueuedNotification current;
            while ((current = inFlightQueue.poll()) != null) {
                if (!removed && current.notification().id().equals(notificationId)) {
                    removed = true;
                } else {
                    remaining.add(current);
                }
            }
            inFlightQueue.addAll(remaining);
            return removed;
        } finally {
            reclaimLock.unlock();
        }
    }

    public int readySize() {
        reclaimExpired();
        return readyQueue.size();
    }

    public int inFlightSize() {
        reclaimExpired();
        return inFlightQueue.size();
    }

    public List<Priority> peekReadyPriorities() {
        reclaimExpired();
        return readyQueue.stream()
                .sorted(Comparator
                        .comparingInt((QueuedNotification q) -> q.notification().priority().weight())
                        .reversed()
                        .thenComparingLong(QueuedNotification::sequence))
                .map(q -> q.notification().priority())
                .toList();
    }

    private void reclaimExpired() {
        reclaimLock.lock();
        try {
            Instant now = clock.instant();
            while (true) {
                QueuedNotification head = inFlightQueue.peek();
                if (head == null || head.visibleAt().isAfter(now)) {
                    break;
                }
                QueuedNotification expired = inFlightQueue.poll();
                if (expired != null) {
                    readyQueue.offer(new QueuedNotification(
                            expired.notification(),
                            expired.sequence(),
                            Instant.MIN));
                }
            }
        } finally {
            reclaimLock.unlock();
        }
    }

    private record QueuedNotification(Notification notification, long sequence, Instant visibleAt) {
    }
}
