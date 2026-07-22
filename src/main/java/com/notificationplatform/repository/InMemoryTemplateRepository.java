package com.notificationplatform.repository;

import com.notificationplatform.domain.NotificationTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryTemplateRepository implements TemplateRepository {

    private final ConcurrentHashMap<String, NotificationTemplate> store = new ConcurrentHashMap<>();

    @Override
    public void save(NotificationTemplate template) {
        Objects.requireNonNull(template, "template");
        store.put(template.id(), template);
    }

    @Override
    public Optional<NotificationTemplate> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<NotificationTemplate> findAll() {
        return new ArrayList<>(store.values());
    }
}
