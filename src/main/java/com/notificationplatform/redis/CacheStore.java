package com.notificationplatform.redis;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-like cache abstraction for template caching and idempotency / rate keys.
 */
public interface CacheStore {

    void set(String key, String value, Duration ttl);

    Optional<String> get(String key);

    boolean setIfAbsent(String key, String value, Duration ttl);

    long increment(String key, Duration ttl);

    void delete(String key);
}
