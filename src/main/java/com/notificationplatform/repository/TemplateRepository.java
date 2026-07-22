package com.notificationplatform.repository;

import com.notificationplatform.domain.NotificationTemplate;

import java.util.List;
import java.util.Optional;

public interface TemplateRepository {

    void save(NotificationTemplate template);

    Optional<NotificationTemplate> findById(String id);

    List<NotificationTemplate> findAll();
}
