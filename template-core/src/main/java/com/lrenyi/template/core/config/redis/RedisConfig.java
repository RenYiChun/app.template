package com.lrenyi.template.core.config.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

public class RedisConfig {
    
    // @formatter:off
    @Bean
    @ConditionalOnClass(name = {"org.springframework.data.redis.core.RedisTemplate"})
    public RedisTemplate<String, byte[]> byteArrayRedisTemplate(
            RedisConnectionFactory connectionFactory,
            PrefixRedisSerializer prefixRedisSerializer) {
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer serializer = new StringRedisSerializer();
        // @formatter:on
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(prefixRedisSerializer);
        template.setHashKeySerializer(serializer);
        
        template.setValueSerializer(RedisSerializer.byteArray());
        template.setHashValueSerializer(RedisSerializer.byteArray());
        template.afterPropertiesSet();
        return template;
    }
    
    @Bean
    public TemplateRedisTemplate stRedisTemplate(RedisConnectionFactory connectionFactory,
                                                 PrefixRedisSerializer prefixRedisSerializer) {
        return new TemplateRedisTemplate(connectionFactory, prefixRedisSerializer);
    }
    
    @Bean
    public PrefixRedisSerializer prefixRedisSerializer() {
        return new PrefixRedisSerializer();
    }
}
