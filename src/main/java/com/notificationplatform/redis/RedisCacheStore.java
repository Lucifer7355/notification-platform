package com.notificationplatform.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class RedisCacheStore implements CacheStore {

    private final StringRedisTemplate redis;

    public RedisCacheStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        redis.opsForValue().set(key, value, ttl);
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(redis.opsForValue().get(key));
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        Boolean ok = redis.opsForValue().setIfAbsent(key, value, ttl);
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public long increment(String key, Duration ttl) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, ttl.toMillis(), TimeUnit.MILLISECONDS);
        }
        return count == null ? 0L : count;
    }

    @Override
    public void delete(String key) {
        redis.delete(key);
    }
}
