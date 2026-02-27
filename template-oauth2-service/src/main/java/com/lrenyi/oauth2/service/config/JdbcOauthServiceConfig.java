package com.lrenyi.oauth2.service.config;

import com.lrenyi.oauth2.service.oauth2.jdbc.OAuth2DatabaseSchemaInitializer;
import com.lrenyi.oauth2.service.oauth2.jdbc.OAuth2InitialDataSeeder;
import com.lrenyi.oauth2.service.oauth2.jdbc.SessionAwareJdbcOAuth2AuthorizationService;
import com.lrenyi.template.core.TemplateConfigProperties;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.oauth2.server.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;

/**
 * authorization.store-type=jdbc 时启用：使用数据库存储 OAuth2 客户端与授权，自动建表并可从配置初始化客户端。
 */
@Configuration
@ConditionalOnProperty(value = "app.template.security.authorization.store-type", havingValue = "jdbc")
@ConditionalOnBean(JdbcOperations.class)
public class JdbcOauthServiceConfig {

    @Bean
    public OAuth2DatabaseSchemaInitializer oauth2DatabaseSchemaInitializer(DataSource dataSource) {
        return new OAuth2DatabaseSchemaInitializer(dataSource);
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcOperations jdbcOperations) {
        return new JdbcRegisteredClientRepository(jdbcOperations);
    }

    @Bean
    public OAuth2AuthorizationService oauth2AuthorizationService(JdbcOperations jdbcOperations,
                                                                 RegisteredClientRepository registeredClientRepository,
                                                                 TemplateConfigProperties templateConfigProperties) {
        OAuth2AuthorizationService jdbcService =
                new JdbcOAuth2AuthorizationService(jdbcOperations, registeredClientRepository);
        return new SessionAwareJdbcOAuth2AuthorizationService(jdbcService, templateConfigProperties);
    }

    @Bean
    public OAuth2InitialDataSeeder oauth2InitialDataSeeder(RegisteredClientRepository registeredClientRepository,
                                                          OAuth2AuthorizationServerProperties properties) {
        return new OAuth2InitialDataSeeder(registeredClientRepository, properties);
    }
}
