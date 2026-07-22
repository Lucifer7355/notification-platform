package com.notificationplatform.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateJpaRepository extends JpaRepository<TemplateEntity, String> {
}
