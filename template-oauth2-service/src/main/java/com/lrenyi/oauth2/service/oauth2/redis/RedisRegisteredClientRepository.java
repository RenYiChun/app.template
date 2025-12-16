package com.lrenyi.oauth2.service.oauth2.redis;

import io.netty.buffer.ByteBufUtil;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

@Slf4j
public class RedisRegisteredClientRepository implements RegisteredClientRepository {
    private static final String REGISTERED_CLIENT_ID_KEY = "registered-client:client_id";
    private static final String REGISTERED_ID_KEY = "registered-client:id";
    private final JdkSerializationStrategy strategy = new JdkSerializationStrategy();
    
    private final RedisTemplate<String, String> stringRedisTemplate;
    private final RegisteredClient[] initialClients;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    public RedisRegisteredClientRepository(RedisTemplate<String, String> stringRedisTemplate,
                                           RegisteredClient... registrations) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.initialClients = registrations != null ? registrations.clone() : new RegisteredClient[0];
        ensureClientsInitialized();
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
        RegisteredClient client = getRegisteredClient(id, REGISTERED_ID_KEY);
        if (client == null) {
            // 如果找不到，尝试恢复并重新查询
            ensureClientsInitialized();
            client = getRegisteredClient(id, REGISTERED_ID_KEY);
        }
        return client;
    }
    
    @Override
    public RegisteredClient findByClientId(String clientId) {
        RegisteredClient client = getRegisteredClient(clientId, REGISTERED_CLIENT_ID_KEY);
        if (client == null) {
            // 如果找不到，尝试恢复并重新查询
            ensureClientsInitialized();
            client = getRegisteredClient(clientId, REGISTERED_CLIENT_ID_KEY);
        }
        return client;
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
    
    /**
     * 检查 Redis 中是否已有客户端数据
     */
    private boolean hasClientsInRedis() {
        try {
            HashOperations<String, String, String> hashOperations = stringRedisTemplate.opsForHash();
            Long size = hashOperations.size(REGISTERED_CLIENT_ID_KEY);
            return size > 0;
        } catch (Exception e) {
            log.warn("检查 Redis 中的客户端配置时发生异常: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 确保客户端已初始化
     * 如果 Redis 中没有数据，则从配置重新初始化
     */
    private void ensureClientsInitialized() {
        lock.writeLock().lock();
        try {
            // 双重检查，避免重复初始化
            if (hasClientsInRedis()) {
                return;
            }
            
            if (initialClients == null || initialClients.length == 0) {
                log.debug("没有初始客户端配置需要初始化");
                return;
            }
            
            log.warn("检测到 Redis 中缺少客户端配置，正在从配置重新初始化 {} 个客户端...", initialClients.length);
            initializeClients();
            log.info("客户端配置重新初始化完成");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 从初始配置初始化所有客户端
     */
    private void initializeClients() {
        for (RegisteredClient registration : initialClients) {
            if (registration != null) {
                try {
                    save(registration);
                    log.debug("已初始化客户端: {}", registration.getClientId());
                } catch (Exception e) {
                    log.error("初始化客户端 {} 时发生异常: {}", registration.getClientId(), e.getMessage(), e);
                }
            }
        }
    }
}
