package com.notificationplatform.repository;

import com.notificationplatform.domain.Notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository {

    void save(Notification notification);

    Optional<Notification> findById(String id);

    List<Notification> findAll();

    List<Notification> findDueScheduled(Instant now);

    List<Notification> findDueRetries(Instant now);
}
