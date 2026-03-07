package com.lrenyi.oauth2.service.oauth2.jdbc;

import com.lrenyi.oauth2.service.config.OAuth2ClientPropertiesMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.security.oauth2.server.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * authorization.store-type=jdbc 时，在表已创建且客户端表为空的情况下，从配置写入初始客户端，与 Redis 的 ensureClientsInitialized 语义一致。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class OAuth2InitialDataSeeder implements ApplicationRunner {
    
    private final RegisteredClientRepository repository;
    private final OAuth2AuthorizationServerProperties properties;
    
    public OAuth2InitialDataSeeder(RegisteredClientRepository repository,
            OAuth2AuthorizationServerProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        RegisteredClient[] clients = OAuth2ClientPropertiesMapper.fromProperties(properties);
        if (clients.length == 0) {
            return;
        }
        int seeded = 0;
        for (RegisteredClient client : clients) {
            if (repository.findByClientId(client.getClientId()) == null) {
                try {
                    repository.save(client);
                    seeded++;
                    log.debug("已初始化 OAuth2 客户端: {}", client.getClientId());
                } catch (Exception e) {
                    log.error("初始化 OAuth2 客户端 {} 时发生异常", client.getClientId(), e);
                }
            }
        }
        if (seeded > 0) {
            log.info("OAuth2 客户端初始数据写入完成，本次写入 {} 个", seeded);
        }
    }
}
