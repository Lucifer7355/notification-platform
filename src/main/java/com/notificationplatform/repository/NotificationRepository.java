package com.notificationplatform.repository;

import com.notificationplatform.domain.Notification;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository {

    void save(Notification notification);

    Optional<Notification> findById(String id);

    List<Notification> findAll();
}
