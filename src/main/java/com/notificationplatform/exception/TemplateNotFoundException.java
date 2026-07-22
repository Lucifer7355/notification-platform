package com.notificationplatform.exception;

public class TemplateNotFoundException extends NotificationException {

    public TemplateNotFoundException(String templateId) {
        super("Template not found: " + templateId);
    }
}
