package com.lrenyi.template.service.config;

import com.lrenyi.template.service.security.CustomFeignClientInterceptor;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

public class DefaultFeignConfig {
    
    @Bean
    public RequestInterceptor feignClientInterceptor() {
        return new CustomFeignClientInterceptor();
    }
}
