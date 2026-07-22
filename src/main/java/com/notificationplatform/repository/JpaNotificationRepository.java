package com.notificationplatform.repository;

import com.notificationplatform.domain.Notification;
import com.notificationplatform.persistence.NotificationEntity;
import com.notificationplatform.persistence.NotificationJpaRepository;
import com.notificationplatform.persistence.NotificationMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaNotificationRepository implements NotificationRepository {

    private final NotificationJpaRepository jpaRepository;

    public JpaNotificationRepository(NotificationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void save(Notification notification) {
        NotificationEntity entity = jpaRepository.findById(notification.id())
                .orElseGet(NotificationEntity::new);
        if (entity.getId() == null) {
            entity = NotificationMapper.toEntity(notification);
        } else {
            NotificationMapper.copyToEntity(notification, entity);
        }
        jpaRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Notification> findById(String id) {
        return jpaRepository.findById(id).map(NotificationMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> findAll() {
        return jpaRepository.findAll().stream().map(NotificationMapper::toDomain).toList();
    }

    @Transactional(readOnly = true)
    public List<Notification> findDueScheduled(Instant now) {
        return jpaRepository.findDueScheduled(now).stream().map(NotificationMapper::toDomain).toList();
    }

    @Transactional(readOnly = true)
    public List<Notification> findDueRetries(Instant now) {
        return jpaRepository.findDueRetries(now).stream().map(NotificationMapper::toDomain).toList();
    }
}
