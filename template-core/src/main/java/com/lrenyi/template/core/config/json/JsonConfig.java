package com.lrenyi.template.core.config.json;

import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

@Configuration
public class JsonConfig {
    
    @Bean
    @Primary
    @Lazy
    public JsonProcessor jsonProcessor(ObjectProvider<JsonProcessor> processors) {
        // 获取所有非Primary的JsonProcessor实现以避免循环依赖
        //@formatter:off
        List<JsonProcessor> availableProcessors = processors.stream()
            .filter(processor -> !processor.getClass().isAnnotationPresent(Primary.class))
            .toList();
        //@formatter:on
        if (availableProcessors.isEmpty()) {
            throw new IllegalStateException("No JsonProcessor implementation found");
        }
        
        // 返回第一个可用的处理器
        return availableProcessors.getFirst();
    }
}
