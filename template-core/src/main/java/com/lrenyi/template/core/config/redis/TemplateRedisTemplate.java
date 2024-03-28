package com.lrenyi.template.core.config.redis;

import lombok.NonNull;
import org.springframework.data.redis.connection.DefaultStringRedisConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

public class TemplateRedisTemplate extends RedisTemplate<String, String> {
    
    public TemplateRedisTemplate(RedisConnectionFactory connectionFactory,
            PrefixRedisSerializer prefixRedisSerializer) {
        this(prefixRedisSerializer);
        setConnectionFactory(connectionFactory);
        afterPropertiesSet();
    }
    
    public TemplateRedisTemplate(PrefixRedisSerializer prefixRedisSerializer) {
        setKeySerializer(prefixRedisSerializer);
        setValueSerializer(RedisSerializer.string());
        setHashKeySerializer(RedisSerializer.string());
        setHashValueSerializer(RedisSerializer.string());
    }
    
    @NonNull
    protected RedisConnection preProcessConnection(@NonNull RedisConnection connection,
            boolean existingConnection) {
        return new DefaultStringRedisConnection(connection);
    }
}
