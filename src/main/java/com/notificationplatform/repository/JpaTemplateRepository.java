package com.notificationplatform.repository;

import com.notificationplatform.domain.NotificationTemplate;
import com.notificationplatform.persistence.TemplateEntity;
import com.notificationplatform.persistence.TemplateJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class JpaTemplateRepository implements TemplateRepository {

    private final TemplateJpaRepository jpaRepository;

    public JpaTemplateRepository(TemplateJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void save(NotificationTemplate template) {
        TemplateEntity entity = jpaRepository.findById(template.id()).orElseGet(TemplateEntity::new);
        entity.setId(template.id());
        entity.setChannel(template.channel());
        entity.setName(template.name());
        entity.setSubjectPattern(template.subjectPattern());
        entity.setBodyPattern(template.bodyPattern());
        entity.setRequiredVars(String.join(",", template.requiredVariables()));
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(Instant.now());
        }
        jpaRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NotificationTemplate> findById(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationTemplate> findAll() {
        return jpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    private NotificationTemplate toDomain(TemplateEntity entity) {
        Set<String> required = Arrays.stream(entity.getRequiredVars().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new NotificationTemplate(
                entity.getId(),
                entity.getChannel(),
                entity.getName(),
                entity.getSubjectPattern(),
                entity.getBodyPattern(),
                required);
    }
}
