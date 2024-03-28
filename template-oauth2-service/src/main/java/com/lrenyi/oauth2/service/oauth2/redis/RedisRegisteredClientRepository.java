package com.lrenyi.oauth2.service.oauth2.redis;

import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

public class RedisRegisteredClientRepository implements RegisteredClientRepository {
    private static final String REGISTERED_CLIENT_ID_KEY = "registered-client:client_id";
    private static final String REGISTERED_ID_KEY = "registered-client:id";
    private final JdkSerializationStrategy strategy = new JdkSerializationStrategy();
    
    private final RedisTemplate<String, byte[]> byteArrayRedisTemplate;
    
    public RedisRegisteredClientRepository(RedisTemplate<String, byte[]> byteArrayRedisTemplate,
            RegisteredClient... registrations) {
        this.byteArrayRedisTemplate = byteArrayRedisTemplate;
        for (RegisteredClient registration : registrations) {
            save(registration);
        }
    }
    
    @Override
    public void save(RegisteredClient registeredClient) {
        byte[] serialize = strategy.serialize(registeredClient);
        HashOperations<String, String, byte[]> hashOperations = byteArrayRedisTemplate.opsForHash();
        
        hashOperations.put(REGISTERED_CLIENT_ID_KEY, registeredClient.getClientId(), serialize);
        hashOperations.put(REGISTERED_ID_KEY, registeredClient.getId(), serialize);
    }
    
    @Override
    public RegisteredClient findById(String id) {
        HashOperations<String, String, byte[]> hashOperations = byteArrayRedisTemplate.opsForHash();
        byte[] value = hashOperations.get(REGISTERED_ID_KEY, id);
        return strategy.deserialize(value, RegisteredClient.class);
    }
    
    @Override
    public RegisteredClient findByClientId(String clientId) {
        HashOperations<String, String, byte[]> hashOperations = byteArrayRedisTemplate.opsForHash();
        byte[] value = hashOperations.get(REGISTERED_CLIENT_ID_KEY, clientId);
        return strategy.deserialize(value, RegisteredClient.class);
    }
}
