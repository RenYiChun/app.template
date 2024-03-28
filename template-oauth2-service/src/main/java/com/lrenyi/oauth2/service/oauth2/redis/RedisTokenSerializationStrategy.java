package com.lrenyi.oauth2.service.oauth2.redis;

public interface RedisTokenSerializationStrategy {
    
    <T> T deserialize(byte[] bytes, Class<T> clazz);
    
    byte[] serialize(Object object);
}

