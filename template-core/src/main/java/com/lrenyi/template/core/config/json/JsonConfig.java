package com.lrenyi.template.core.config.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * JSON配置类
 * 支持多种JSON处理器的自动配置
 */
@Slf4j
@Configuration
public class JsonConfig {
    
    /**
     * 默认Jackson JSON处理器
     * 当存在ObjectMapper且没有指定其他处理器时使用
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(JsonProcessor.class)
    @ConditionalOnBean(ObjectMapper.class)
    @ConditionalOnProperty(name = "app.config.json.processor-type", havingValue = "jackson", matchIfMissing = true)
    public JsonProcessor jacksonJsonProcessor(ObjectMapper objectMapper) {
        log.info("Creating Jackson JSON processor");
        return new JacksonJsonProcessor(objectMapper);
    }
    
    /**
     * 工厂模式JSON处理器
     * 当没有其他处理器可用时，使用工厂创建默认处理器
     */
    @Bean
    @ConditionalOnMissingBean(JsonProcessor.class)
    public JsonProcessor factoryJsonProcessor(@Value(
            "${app.config.json.processor-type:jackson}"
    ) String processorType) {
        log.info("Creating JSON processor via factory with type: {}", processorType);
        return JsonProcessorFactory.createProcessor(processorType);
    }
    
    /**
     * JSON处理器工厂Bean
     * 提供工厂实例用于运行时创建处理器
     */
    @Bean
    @ConditionalOnMissingBean(JsonProcessorFactory.class)
    public JsonProcessorFactory jsonProcessorFactory() {
        return new JsonProcessorFactory();
    }
}
