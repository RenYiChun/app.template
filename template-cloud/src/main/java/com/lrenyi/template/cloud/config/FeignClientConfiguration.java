package com.lrenyi.template.cloud.config;

import com.lrenyi.template.cloud.service.OauthUtilService;
import com.lrenyi.template.core.TemplateConfigProperties;
import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

public class FeignClientConfiguration {
    
    @Bean
    @ConditionalOnProperty(name = "app.template.feign.enabled", havingValue = "true", matchIfMissing = true)
    public RequestInterceptor requestInterceptor(TemplateConfigProperties templateConfigProperties,
            OauthUtilService oauthUtilService) {
        return new TemplateRequestInterceptor(templateConfigProperties, oauthUtilService);
    }
}