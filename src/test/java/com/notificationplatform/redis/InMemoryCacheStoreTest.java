package com.notificationplatform.redis;

import com.notificationplatform.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryCacheStoreTest {

    @Test
    void setIfAbsent_andTtlExpiry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryCacheStore cache = new InMemoryCacheStore(clock);

        assertThat(cache.setIfAbsent("k", "v", Duration.ofSeconds(10))).isTrue();
        assertThat(cache.setIfAbsent("k", "v2", Duration.ofSeconds(10))).isFalse();
        assertThat(cache.get("k")).contains("v");

        clock.advance(Duration.ofSeconds(10));
        assertThat(cache.get("k")).isEmpty();
    }

    @Test
    void increment_countsWithinWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryCacheStore cache = new InMemoryCacheStore(clock);

        assertThat(cache.increment("rate", Duration.ofMinutes(1))).isEqualTo(1);
        assertThat(cache.increment("rate", Duration.ofMinutes(1))).isEqualTo(2);
    }
}
