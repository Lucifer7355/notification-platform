package com.notificationplatform.template;

import com.notificationplatform.domain.ChannelType;
import com.notificationplatform.domain.NotificationTemplate;
import com.notificationplatform.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void render_validVariables_substitutesPlaceholders() {
        NotificationTemplate template = new NotificationTemplate(
                "t1", ChannelType.EMAIL, "Welcome", "Hi {{name}}", "Hello {{name}} from {{product}}",
                Set.of("name", "product"));

        TemplateRenderer.RenderedTemplate rendered = renderer.render(template, Map.of("name", "Ada", "product", "NP"));

        assertThat(rendered.subject()).isEqualTo("Hi Ada");
        assertThat(rendered.body()).isEqualTo("Hello Ada from NP");
    }

    @Test
    void render_missingRequiredVariable_throwsValidationException() {
        NotificationTemplate template = new NotificationTemplate(
                "t1", ChannelType.SMS, "OTP", "OTP", "code {{otp}}", Set.of("otp"));

        assertThatThrownBy(() -> renderer.render(template, Map.of()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("otp");
    }
}
