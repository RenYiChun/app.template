package com.lrenyi.template.core.autoconfigure;

import com.lrenyi.template.core.CoreAutoConfiguration;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.coder.DefaultTemplateEncryptService;
import com.lrenyi.template.core.json.JsonService;
import com.lrenyi.template.core.metrics.AppMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class CoreAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    CoreAutoConfiguration.class
            ));

    @Test
    void missingAppTemplateEnabledTreatsRuntimeFeaturesAsDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            TemplateConfigProperties properties = context.getBean(TemplateConfigProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.isSecurityEffectivelyEnabled()).isFalse();
            assertThat(properties.isFlowEffectivelyEnabled()).isFalse();
            assertThat(properties.isFeignEffectivelyEnabled()).isFalse();
            assertThat(properties.isOauth2EffectivelyEnabled()).isFalse();
            assertThat(properties.isAuditEffectivelyEnabled()).isFalse();
            assertThat(properties.isMethodSecurityEffectivelyEnabled()).isFalse();
        });
    }

    @Test
    void appTemplateDisabledStillRegistersCoreSupportBeans() {
        contextRunner.withPropertyValues("app.template.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(JsonService.class);
            assertThat(context).hasSingleBean(AppMetrics.class);
            assertThat(context).hasSingleBean(PasswordEncoder.class);
            assertThat(context).hasSingleBean(DefaultTemplateEncryptService.class);
        });
    }
}
