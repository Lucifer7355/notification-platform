package com.notificationplatform.retry;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ExponentialBackoffRetryPolicyTest {

    @Test
    void nextDelay_growsExponentiallyUntilMax() {
        ExponentialBackoffRetryPolicy policy = new ExponentialBackoffRetryPolicy(
                5,
                Duration.ofMillis(100),
                2.0,
                Duration.ofMillis(300));

        assertThat(policy.nextDelay(1)).isEqualTo(Duration.ofMillis(100));
        assertThat(policy.nextDelay(2)).isEqualTo(Duration.ofMillis(200));
        assertThat(policy.nextDelay(3)).isEqualTo(Duration.ofMillis(300));
        assertThat(policy.shouldRetry(4)).isTrue();
        assertThat(policy.shouldRetry(5)).isFalse();
    }
}
