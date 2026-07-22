package com.notificationplatform;

import com.notificationplatform.config.PlatformProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(PlatformProperties.class)
public class NotificationPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationPlatformApplication.class, args);
    }
}
