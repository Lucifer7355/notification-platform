package com.notificationplatform.redis;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryCacheStore implements CacheStore {

    private final ConcurrentHashMap<String, CacheEntry> store = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryCacheStore(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(ttl, "ttl");
        store.put(key, new CacheEntry(value, clock.instant().plus(ttl)));
    }

    @Override
    public Optional<String> get(String key) {
        CacheEntry entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(clock.instant()) || entry.expiresAt().equals(clock.instant())) {
            store.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(ttl, "ttl");
        purgeIfExpired(key);
        CacheEntry created = new CacheEntry(value, clock.instant().plus(ttl));
        return store.putIfAbsent(key, created) == null;
    }

    @Override
    public long increment(String key, Duration ttl) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(ttl, "ttl");
        Instant expiresAt = clock.instant().plus(ttl);
        while (true) {
            purgeIfExpired(key);
            CacheEntry current = store.get(key);
            if (current == null) {
                CacheEntry created = new CacheEntry("1", expiresAt);
                if (store.putIfAbsent(key, created) == null) {
                    return 1L;
                }
            } else {
                long next = Long.parseLong(current.value()) + 1;
                CacheEntry updated = new CacheEntry(Long.toString(next), expiresAt);
                if (store.replace(key, current, updated)) {
                    return next;
                }
            }
        }
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }

    private void purgeIfExpired(String key) {
        CacheEntry entry = store.get(key);
        if (entry != null && !entry.expiresAt().isAfter(clock.instant())) {
            store.remove(key, entry);
        }
    }

    private record CacheEntry(String value, Instant expiresAt) {
    }
}
