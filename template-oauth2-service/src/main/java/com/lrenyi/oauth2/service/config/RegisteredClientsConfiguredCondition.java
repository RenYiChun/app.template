package com.lrenyi.oauth2.service.config;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.autoconfigure.security.oauth2.server.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class RegisteredClientsConfiguredCondition extends SpringBootCondition {

    private static final String PROPERTY_PREFIX = "spring.security.oauth2.authorizationserver";

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        BindResult<OAuth2AuthorizationServerProperties> result = Binder.get(context.getEnvironment())
                .bind(PROPERTY_PREFIX, OAuth2AuthorizationServerProperties.class);
        if (result.isBound() && !result.get().getClient().isEmpty()) {
            return ConditionOutcome.match("OAuth2 registered clients configured");
        }
        return ConditionOutcome.noMatch("OAuth2 registered clients not configured");
    }
}
