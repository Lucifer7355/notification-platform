package com.notificationplatform.template;

import com.notificationplatform.domain.NotificationTemplate;
import com.notificationplatform.exception.ValidationException;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}");

    public RenderedTemplate render(NotificationTemplate template, Map<String, String> variables) {
        Objects.requireNonNull(template, "template");
        Map<String, String> safeVariables = variables == null ? Map.of() : variables;

        Map<String, String> missing = template.missingVariables(safeVariables);
        if (!missing.isEmpty()) {
            throw new ValidationException(
                    "Missing required template variables for " + template.id() + ": " + missing.keySet());
        }

        String subject = substitute(template.subjectPattern(), safeVariables);
        String body = substitute(template.bodyPattern(), safeVariables);
        return new RenderedTemplate(subject, body);
    }

    private String substitute(String pattern, Map<String, String> variables) {
        Matcher matcher = PLACEHOLDER.matcher(pattern);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables.get(key);
            if (value == null) {
                throw new ValidationException("Unresolved template placeholder: {{" + key + "}}");
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public record RenderedTemplate(String subject, String body) {
    }
}
