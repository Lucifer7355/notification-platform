package com.notificationplatform.worker;

import com.notificationplatform.config.PlatformProperties;
import com.notificationplatform.service.NotificationPlatformService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationKafkaListener.class);

    private final NotificationPlatformService platformService;

    public NotificationKafkaListener(NotificationPlatformService platformService) {
        this.platformService = platformService;
    }

    @KafkaListener(topics = "${platform.kafka-topic}", groupId = "notification-workers")
    public void onNotification(String notificationId) {
        log.info("Kafka message received notificationId={}", notificationId);
        platformService.enqueueFromKafka(notificationId);
        platformService.processAvailable(1);
    }
}
