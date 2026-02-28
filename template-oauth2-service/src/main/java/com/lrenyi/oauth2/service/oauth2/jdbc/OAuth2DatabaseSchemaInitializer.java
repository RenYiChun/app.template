package com.lrenyi.oauth2.service.oauth2.jdbc;

import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * authorization.store-type=jdbc 时，在启动时执行 OAuth2 表结构脚本（幂等），对用户透明。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OAuth2DatabaseSchemaInitializer implements ApplicationRunner {
    
    private final DataSource dataSource;
    
    public OAuth2DatabaseSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("schema/oauth2/oauth2_registered_client.sql"));
        populator.addScript(new ClassPathResource("schema/oauth2/oauth2_authorization.sql"));
        populator.setContinueOnError(false);
        populator.execute(dataSource);
    }
}
