package com.notificationplatform.persistence;

import com.notificationplatform.domain.ChannelType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "templates")
public class TemplateEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ChannelType channel;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "subject_pattern", nullable = false, columnDefinition = "TEXT")
    private String subjectPattern;

    @Column(name = "body_pattern", nullable = false, columnDefinition = "TEXT")
    private String bodyPattern;

    @Column(name = "required_vars", nullable = false, columnDefinition = "TEXT")
    private String requiredVars;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ChannelType getChannel() {
        return channel;
    }

    public void setChannel(ChannelType channel) {
        this.channel = channel;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSubjectPattern() {
        return subjectPattern;
    }

    public void setSubjectPattern(String subjectPattern) {
        this.subjectPattern = subjectPattern;
    }

    public String getBodyPattern() {
        return bodyPattern;
    }

    public void setBodyPattern(String bodyPattern) {
        this.bodyPattern = bodyPattern;
    }

    public String getRequiredVars() {
        return requiredVars;
    }

    public void setRequiredVars(String requiredVars) {
        this.requiredVars = requiredVars;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
