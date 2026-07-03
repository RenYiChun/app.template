package com.lrenyi.template.cloud;

import com.lrenyi.template.api.ApiAutoConfiguration;
import com.lrenyi.template.core.CoreAutoConfiguration;
import feign.Retryer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

import static org.assertj.core.api.Assertions.assertThat;

class CloudAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner contextRunner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    CoreAutoConfiguration.class,
                    CloudAutoConfiguration.class
            ));

    @Test
    void feignRetryerBacksOffWhenAppTemplateSwitchIsMissing() {
        contextRunner.withPropertyValues("app.template.feign.retry.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(Retryer.class);
        });
    }

    @Test
    void feignRetryerLoadsWhenAppTemplateAndRetrySwitchesAreEnabled() {
        contextRunner.withPropertyValues(
                "app.template.enabled=true",
                "app.template.feign.retry.enabled=true"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(Retryer.class);
        });
    }

    @Test
    void opaqueTokenIntrospectorLoadsInReactiveContextWithoutServletSecurityConfiguration() {
        contextRunner.withPropertyValues(
                    "app.template.enabled=true",
                    "app.template.oauth2.enabled=true",
                    "app.template.oauth2.opaque-token.enabled=true",
                    "app.template.oauth2.opaque-token.introspection-uri=http://auth-service/oauth2/introspect",
                    "app.template.oauth2.opaque-token.client-id=test-client",
                    "app.template.oauth2.opaque-token.client-secret=test-secret"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OpaqueTokenIntrospector.class);
            assertThat(context).doesNotHaveBean(ApiAutoConfiguration.SecurityAutoConfiguration.class);
        });
    }

    @Test
    void opaqueTokenIntrospectorBacksOffWhenOpaqueTokenSwitchIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(OpaqueTokenIntrospector.class);
        });
    }

    @Test
    void opaqueTokenIntrospectorBacksOffWhenAppTemplateDisabled() {
        contextRunner.withPropertyValues(
                "app.template.enabled=false",
                "app.template.oauth2.enabled=true",
                "app.template.oauth2.opaque-token.enabled=true"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(OpaqueTokenIntrospector.class);
        });
    }

    @Test
    void opaqueTokenIntrospectorBacksOffWhenOauth2Disabled() {
        contextRunner.withPropertyValues(
                "app.template.oauth2.enabled=false",
                "app.template.oauth2.opaque-token.enabled=true"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(OpaqueTokenIntrospector.class);
        });
    }
}
