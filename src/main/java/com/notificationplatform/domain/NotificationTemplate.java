package com.notificationplatform.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class NotificationTemplate {

    private final String id;
    private final ChannelType channel;
    private final String name;
    private final String subjectPattern;
    private final String bodyPattern;
    private final Set<String> requiredVariables;

    public NotificationTemplate(
            String id,
            ChannelType channel,
            String name,
            String subjectPattern,
            String bodyPattern,
            Set<String> requiredVariables) {
        this.id = Objects.requireNonNull(id, "id");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.name = Objects.requireNonNull(name, "name");
        this.subjectPattern = Objects.requireNonNull(subjectPattern, "subjectPattern");
        this.bodyPattern = Objects.requireNonNull(bodyPattern, "bodyPattern");
        this.requiredVariables = Collections.unmodifiableSet(
                Set.copyOf(Objects.requireNonNull(requiredVariables, "requiredVariables")));
    }

    public String id() {
        return id;
    }

    public ChannelType channel() {
        return channel;
    }

    public String name() {
        return name;
    }

    public String subjectPattern() {
        return subjectPattern;
    }

    public String bodyPattern() {
        return bodyPattern;
    }

    public Set<String> requiredVariables() {
        return requiredVariables;
    }

    public Map<String, String> missingVariables(Map<String, String> provided) {
        Map<String, String> missing = new LinkedHashMap<>();
        Map<String, String> safe = provided == null ? Map.of() : provided;
        for (String key : requiredVariables) {
            String value = safe.get(key);
            if (value == null || value.isBlank()) {
                missing.put(key, "required");
            }
        }
        return missing;
    }
}
