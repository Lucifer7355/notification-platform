package com.notificationplatform.retry;

import java.time.Duration;

public interface RetryPolicy {

    boolean shouldRetry(int attemptCount);

    Duration nextDelay(int attemptCount);

    int maxAttempts();
}
