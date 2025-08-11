package com.lrenyi.oauth2.service.oauth2.redis;

import io.netty.buffer.ByteBufUtil;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

public class RedisRegisteredClientRepository implements RegisteredClientRepository {
    private static final String REGISTERED_CLIENT_ID_KEY = "registered-client:client_id";
    private static final String REGISTERED_ID_KEY = "registered-client:id";
    private final JdkSerializationStrategy strategy = new JdkSerializationStrategy();
    
    private final RedisTemplate<String, String> stringRedisTemplate;
    
    public RedisRegisteredClientRepository(RedisTemplate<String, String> stringRedisTemplate,
            RegisteredClient... registrations) {
        this.stringRedisTemplate = stringRedisTemplate;
        for (RegisteredClient registration : registrations) {
            save(registration);
        }
    }
    
    @Override
    public void save(RegisteredClient registeredClient) {
        byte[] serialize = strategy.serialize(registeredClient);
        HashOperations<String, String, String> hashOperations = stringRedisTemplate.opsForHash();
        String hexed = ByteBufUtil.hexDump(serialize);
        hashOperations.put(REGISTERED_CLIENT_ID_KEY, registeredClient.getClientId(), hexed);
        hashOperations.put(REGISTERED_ID_KEY, registeredClient.getId(), hexed);
    }
    
    @Override
    public RegisteredClient findById(String id) {
        return getRegisteredClient(id, REGISTERED_ID_KEY);
    }
    
    @Override
    public RegisteredClient findByClientId(String clientId) {
        return getRegisteredClient(clientId, REGISTERED_CLIENT_ID_KEY);
    }
    
    private RegisteredClient getRegisteredClient(String clientId, String registeredClientIdKey) {
        HashOperations<String, String, String> hashOperations = stringRedisTemplate.opsForHash();
        String hexed = hashOperations.get(registeredClientIdKey, clientId);
        if (hexed != null) {
            byte[] value = ByteBufUtil.decodeHexDump(hexed);
            return strategy.deserialize(value, RegisteredClient.class);
        }
        return null;
    }
}
