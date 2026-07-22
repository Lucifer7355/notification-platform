package com.notificationplatform.persistence;

import com.notificationplatform.domain.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, String> {

    @Query("""
            select n from NotificationEntity n
            where n.status = com.notificationplatform.domain.NotificationStatus.SCHEDULED
              and n.scheduledAt <= :now
            order by n.scheduledAt asc
            """)
    List<NotificationEntity> findDueScheduled(@Param("now") Instant now);

    @Query("""
            select n from NotificationEntity n
            where n.status = com.notificationplatform.domain.NotificationStatus.RETRYING
              and n.nextRetryAt <= :now
            order by n.nextRetryAt asc
            """)
    List<NotificationEntity> findDueRetries(@Param("now") Instant now);

    long countByStatus(NotificationStatus status);
}
