package com.lrenyi.oauth2.service.config;

import com.lrenyi.oauth2.service.oauth2.redis.RedisOAuth2AuthorizationService;
import com.lrenyi.oauth2.service.oauth2.redis.RedisRegisteredClientRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.oauth2.server.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

public class RedisOauthServiceConfig {
    
    @Bean
    @ConditionalOnProperty(value = "app.template.security.authorization.store-type", havingValue = "redis")
    public OAuth2AuthorizationService redisOauth2AuthorizationService(RedisTemplate<String, String> templateStringRedis) {
        return new RedisOAuth2AuthorizationService(templateStringRedis);
    }
    
    @Bean
    @ConditionalOnProperty(value = "app.template.security.authorization.store-type", havingValue = "redis")
    public RegisteredClientRepository registerClientRepository(RedisTemplate<String, String> templateStringRedis,
            OAuth2AuthorizationServerProperties properties) {
        return new RedisRegisteredClientRepository(templateStringRedis,
                                                   OAuth2ClientPropertiesMapper.fromProperties(properties)
        );
    }
}
