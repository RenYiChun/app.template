package com.lrenyi.template.cloud.config;

import com.lrenyi.template.core.TemplateConfigProperties;
import feign.RequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@Slf4j
public class FeignClientConfiguration {
    
    @Bean
    @ConditionalOnProperty(name = "app.template.feign.enabled", havingValue = "true", matchIfMissing = true)
    public RequestInterceptor requestInterceptor(TemplateConfigProperties templateConfigProperties) {
        return new TemplateRequestInterceptor(templateConfigProperties);
    }
}