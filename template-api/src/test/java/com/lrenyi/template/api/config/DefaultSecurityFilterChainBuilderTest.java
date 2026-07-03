package com.lrenyi.template.api.config;

import java.util.function.Consumer;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.json.JsonService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultSecurityFilterChainBuilderTest {

    @Test
    @SuppressWarnings("unchecked")
    void canBeCreatedWithoutOpaqueTokenIntrospectorBean() {
        ObjectProvider<Consumer<HttpSecurity>> httpConfigurerProvider = mock(ObjectProvider.class);
        ObjectProvider<JsonService> jsonServiceProvider = mock(ObjectProvider.class);
        ObjectProvider<OpaqueTokenIntrospector> opaqueTokenIntrospectorProvider = mock(ObjectProvider.class);
        when(opaqueTokenIntrospectorProvider.getIfAvailable()).thenReturn(null);

        DefaultSecurityFilterChainBuilder builder = new DefaultSecurityFilterChainBuilder(
                mock(RsaPublicAndPrivateKey.class),
                mock(Environment.class),
                httpConfigurerProvider,
                new TemplateConfigProperties(),
                jsonServiceProvider,
                new JwtAuthenticationConverter(),
                opaqueTokenIntrospectorProvider,
                new SimpleMeterRegistry()
        );

        assertNotNull(builder);
    }
}
