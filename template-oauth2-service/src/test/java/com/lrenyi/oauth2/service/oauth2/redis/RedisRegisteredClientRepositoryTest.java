package com.lrenyi.oauth2.service.oauth2.redis;

import io.netty.buffer.ByteBufUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RedisRegisteredClientRepository 单元测试
 */
@ExtendWith(MockitoExtension.class)
class RedisRegisteredClientRepositoryTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private HashOperations<String, String, String> hashOperations;

    private static RegisteredClient createClient(String id, String clientId) {
        return RegisteredClient.withId(id)
                .clientId(clientId)
                .clientSecret("secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("read")
                .build();
    }

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenAnswer(invocation -> hashOperations);
    }

    @Test
    void findById_whenInRedis_returnsClient() {
        RegisteredClient client = createClient("id-1", "client-a");
        byte[] serialized = new JdkSerializationStrategy().serialize(client);
        String hexed = ByteBufUtil.hexDump(serialized);
        when(hashOperations.get(eq("registered-client:id"), eq("id-1"))).thenReturn(hexed);
        when(hashOperations.size("registered-client:client_id")).thenReturn(1L);

        RedisRegisteredClientRepository repo =
                new RedisRegisteredClientRepository(redisTemplate);

        RegisteredClient found = repo.findById("id-1");

        assertNotNull(found);
        assertEquals("id-1", found.getId());
        assertEquals("client-a", found.getClientId());
    }

    @Test
    void findByClientId_whenInRedis_returnsClient() {
        RegisteredClient client = createClient("id-2", "client-b");
        byte[] serialized = new JdkSerializationStrategy().serialize(client);
        String hexed = ByteBufUtil.hexDump(serialized);
        when(hashOperations.get(eq("registered-client:client_id"), eq("client-b"))).thenReturn(hexed);
        when(hashOperations.size("registered-client:client_id")).thenReturn(1L);

        RedisRegisteredClientRepository repo =
                new RedisRegisteredClientRepository(redisTemplate);

        RegisteredClient found = repo.findByClientId("client-b");

        assertNotNull(found);
        assertEquals("id-2", found.getId());
        assertEquals("client-b", found.getClientId());
    }

    @Test
    void findById_whenNotInRedis_returnsNull() {
        when(hashOperations.get(eq("registered-client:id"), anyString())).thenReturn(null);
        when(hashOperations.size("registered-client:client_id")).thenReturn(0L);

        RedisRegisteredClientRepository repo =
                new RedisRegisteredClientRepository(redisTemplate);

        RegisteredClient found = repo.findById("unknown-id");

        assertNull(found);
    }

    @Test
    void save_persistsToRedis() {
        when(hashOperations.size("registered-client:client_id")).thenReturn(1L);

        RegisteredClient client = createClient("id-3", "client-c");
        RedisRegisteredClientRepository repo =
                new RedisRegisteredClientRepository(redisTemplate);

        repo.save(client);

        verify(hashOperations).put(eq("registered-client:client_id"), eq("client-c"), anyString());
        verify(hashOperations).put(eq("registered-client:id"), eq("id-3"), anyString());
    }

    @Test
    void constructor_withInitialClients_initializesWhenRedisEmpty() {
        when(hashOperations.size("registered-client:client_id")).thenReturn(0L);

        RegisteredClient client = createClient("id-init", "client-init");
        RedisRegisteredClientRepository repo =
                new RedisRegisteredClientRepository(redisTemplate, client);

        verify(hashOperations).put(eq("registered-client:client_id"), eq("client-init"), anyString());
    }
}
