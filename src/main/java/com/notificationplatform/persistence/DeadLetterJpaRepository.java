package com.notificationplatform.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeadLetterJpaRepository extends JpaRepository<DeadLetterEntity, Long> {

    List<DeadLetterEntity> findAllByOrderByDeadLetteredAtDesc();
}
