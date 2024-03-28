package com.lrenyi.template.core.config.nats;

import com.lrenyi.template.core.nats.TemplateNatsService;
import io.nats.client.Connection;
import org.springframework.context.annotation.Bean;

public class NatsConfig {
    
    @Bean
    public TemplateNatsService templateNatsService(Connection connection) {
        return new TemplateNatsService(connection);
    }
}
