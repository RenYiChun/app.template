package com.lrenyi.template.api;

import com.lrenyi.template.api.config.DefaultSecurityFilterChainBuilder;
import com.lrenyi.template.api.config.GlobalExceptionHandler;
import com.lrenyi.template.core.CoreAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    CoreAutoConfiguration.class
            ))
            .withUserConfiguration(ApiSecurityConfiguration.class, SecurityChainTestConfiguration.class)
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withPropertyValues("app.template.security.enabled=false");

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

    @Test
    void globalExceptionHandlerBacksOffWhenAppTemplateSwitchIsMissing() {
        contextRunner.withUserConfiguration(GlobalExceptionHandler.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(GlobalExceptionHandler.class);
        });
    }

    @Test
    void globalExceptionHandlerLoadsWhenAppTemplateSwitchIsEnabled() {
        contextRunner.withUserConfiguration(GlobalExceptionHandler.class)
                     .withPropertyValues("app.template.enabled=true")
                     .run(context -> {
                         assertThat(context).hasNotFailed();
                         assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
                     });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(ApiAutoConfiguration.SecurityAutoConfiguration.class)
    static class ApiSecurityConfiguration {
        // 仅导入 SecurityAutoConfiguration，避免 ApiAutoConfiguration 的组件扫描拉起 WebSocket。
    }

    @Configuration(proxyBeanMethods = false)
    static class SecurityChainTestConfiguration {

        @Bean
        HttpSecurity httpSecurity() {
            return mock(HttpSecurity.class);
        }

        @Bean
        DefaultSecurityFilterChainBuilder defaultSecurityFilterChainBuilder() throws Exception {
            DefaultSecurityFilterChainBuilder builder = mock(DefaultSecurityFilterChainBuilder.class);
            when(builder.build(any(HttpSecurity.class))).thenReturn(mock(SecurityFilterChain.class));
            return builder;
        }
    }
}
