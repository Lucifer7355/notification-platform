package com.notificationplatform.worker;

import com.notificationplatform.config.PlatformProperties;
import com.notificationplatform.service.NotificationPlatformService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationWorkers {

    private static final Logger log = LoggerFactory.getLogger(NotificationWorkers.class);

    private final NotificationPlatformService platformService;
    private final PlatformProperties properties;

    public NotificationWorkers(NotificationPlatformService platformService, PlatformProperties properties) {
        this.platformService = platformService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${platform.scheduler-fixed-delay}")
    public void releaseScheduled() {
        int released = platformService.releaseDueScheduled();
        if (released > 0) {
            log.info("Released {} scheduled notifications to Kafka", released);
        }
    }

    @Scheduled(fixedDelayString = "${platform.retry-fixed-delay}")
    public void releaseRetriesAndDrain() {
        int retries = platformService.releaseDueRetries();
        int processed = platformService.processAvailable(properties.workerBatchSize());
        if (retries > 0 || processed > 0) {
            log.info("Released retries={}, processed={}", retries, processed);
        }
    }
}
