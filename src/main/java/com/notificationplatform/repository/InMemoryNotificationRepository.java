package com.notificationplatform.repository;

import com.notificationplatform.domain.Notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryNotificationRepository implements NotificationRepository {

    private final ConcurrentHashMap<String, Notification> store = new ConcurrentHashMap<>();

    @Override
    public void save(Notification notification) {
        Objects.requireNonNull(notification, "notification");
        store.put(notification.id(), notification);
    }

    @Override
    public Optional<Notification> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Notification> findAll() {
        return new ArrayList<>(store.values());
    }
}
