package com.lrenyi.template.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.core.coder.DefaultTemplateEncryptService;
import com.lrenyi.template.core.json.JacksonJsonProcessor;
import com.lrenyi.template.core.json.JsonProcessor;
import com.lrenyi.template.core.json.JsonService;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(CoreAutoConfiguration.WebConfig.class)
@AutoConfigureAfter(JacksonAutoConfiguration.class)
@EnableConfigurationProperties(TemplateConfigProperties.class)
public class CoreAutoConfiguration {
    
    @ConditionalOnProperty(name = "app.template.enabled", havingValue = "true")
    static class WebConfig {
        @Bean
        @ConditionalOnMissingBean(JsonProcessor.class)
        @ConditionalOnProperty(
                name = "app.template.web.json-processor-type", havingValue = "jackson", matchIfMissing = true
        )
        public JsonProcessor jacksonJsonProcessor(ObjectMapper objectMapper) {
            return new JacksonJsonProcessor(objectMapper);
        }
        
        @Bean
        @ConditionalOnMissingBean
        public JsonService jsonService(JsonProcessor jsonProcessor) {
            return new JsonService(jsonProcessor);
        }
        
        @Bean
        @ConditionalOnMissingBean
        public DefaultTemplateEncryptService defaultTemplateEncryptService(
                TemplateConfigProperties templateConfigProperties) {
            return new DefaultTemplateEncryptService(templateConfigProperties);
        }
    }
}
