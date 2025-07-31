package com.lrenyi.oauth2.service.config;

import com.lrenyi.oauth2.service.oauth2.redis.RedisOAuth2AuthorizationService;
import com.lrenyi.oauth2.service.oauth2.redis.RedisRegisteredClientRepository;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.oauth2.server.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

public class RedisOauthServiceConfig {
    
    @Bean
    @ConditionalOnProperty(value = "app.template.security.authorization-type", havingValue = "redis")
    public OAuth2AuthorizationService redisOauth2AuthorizationService() {
        return new RedisOAuth2AuthorizationService();
    }
    
    @Bean
    @ConditionalOnProperty(value = "app.template.security.authorization-type", havingValue = "redis")
    public RegisteredClientRepository registerClientRepository(RedisTemplate<String, byte[]> brt,
                                                               OAuth2AuthorizationServerProperties properties) {
        OAuth2AuthorizationServerPropertiesMapper mapper = new OAuth2AuthorizationServerPropertiesMapper(properties);
        List<RegisteredClient> registeredClients = mapper.asRegisteredClients();
        return new RedisRegisteredClientRepository(brt, registeredClients.toArray(new RegisteredClient[0]));
    }
    
    @Bean
    @ConditionalOnMissingBean
    public RedisTemplate<String, byte[]> byteArrayRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // 设置key的序列化方式
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // 设置value的序列化方式 - 对于byte[]，使用原始字节序列化器
        template.setValueSerializer(RedisSerializer.byteArray());
        template.setHashValueSerializer(RedisSerializer.byteArray());
        
        template.afterPropertiesSet();
        return template;
    }
}
