package com.notificationplatform.service;

import com.notificationplatform.domain.NotificationTemplate;
import com.notificationplatform.exception.TemplateNotFoundException;
import com.notificationplatform.redis.CacheStore;
import com.notificationplatform.repository.TemplateRepository;
import com.notificationplatform.template.TemplateRenderer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;

@Service
public class TemplateService {

    private static final Duration TEMPLATE_CACHE_TTL = Duration.ofMinutes(10);

    private final TemplateRepository templateRepository;
    private final TemplateRenderer templateRenderer;
    private final CacheStore cacheStore;

    public TemplateService(
            TemplateRepository templateRepository,
            TemplateRenderer templateRenderer,
            CacheStore cacheStore) {
        this.templateRepository = templateRepository;
        this.templateRenderer = templateRenderer;
        this.cacheStore = cacheStore;
    }

    @Transactional
    public void register(NotificationTemplate template) {
        templateRepository.save(template);
        cacheStore.set(cacheKey(template.id()), template.id(), TEMPLATE_CACHE_TTL);
    }

    @Transactional(readOnly = true)
    public NotificationTemplate require(String templateId) {
        cacheStore.get(cacheKey(templateId)).orElseGet(() -> {
            NotificationTemplate loaded = templateRepository.findById(templateId)
                    .orElseThrow(() -> new TemplateNotFoundException(templateId));
            cacheStore.set(cacheKey(templateId), loaded.id(), TEMPLATE_CACHE_TTL);
            return loaded.id();
        });
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new TemplateNotFoundException(templateId));
    }

    @Transactional(readOnly = true)
    public TemplateRenderer.RenderedTemplate render(String templateId, Map<String, String> variables) {
        NotificationTemplate template = require(templateId);
        return templateRenderer.render(template, variables);
    }

    public boolean isCached(String templateId) {
        return cacheStore.get(cacheKey(templateId)).isPresent();
    }

    private static String cacheKey(String templateId) {
        return "template:" + templateId;
    }
}
