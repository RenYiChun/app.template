package com.lrenyi.template.core.config.nats;

import com.lrenyi.spring.nats.ConnectionHolder;
import com.lrenyi.template.core.nats.TemplateNatsService;
import org.springframework.context.annotation.Bean;

public class NatsConfig {
    
    @Bean
    public TemplateNatsService templateNatsService(ConnectionHolder connectionHolder) {
        return new TemplateNatsService(connectionHolder);
    }
}
