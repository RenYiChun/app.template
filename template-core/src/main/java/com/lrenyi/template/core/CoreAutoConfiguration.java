package com.lrenyi.template.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.core.json.JacksonJsonProcessor;
import com.lrenyi.template.core.json.JsonProcessor;
import com.lrenyi.template.core.json.JsonService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TemplateConfigProperties.class)
@ConditionalOnProperty(name = "app.template.enabled", matchIfMissing = true)
public class CoreAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean(JsonProcessor.class)
    @ConditionalOnBean(ObjectMapper.class)
    @ConditionalOnProperty(
            name = "app.template.web.json-processor-type", havingValue = "jackson", matchIfMissing = true
    )
    public JsonProcessor jacksonJsonProcessor(ObjectMapper objectMapper) {
        return new JacksonJsonProcessor(objectMapper);
    }
    
    @Bean
    @ConditionalOnBean(JsonProcessor.class)
    @ConditionalOnMissingBean(JsonService.class)
    public JsonService jsonService(JsonProcessor jsonProcessor) {
        return new JsonService(jsonProcessor);
    }
    
}
