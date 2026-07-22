package com.notificationplatform.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryAttemptJpaRepository extends JpaRepository<DeliveryAttemptEntity, Long> {

    List<DeliveryAttemptEntity> findByNotificationIdOrderByAttemptNumberAsc(String notificationId);
}
