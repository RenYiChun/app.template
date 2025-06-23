package com.lrenyi.template.web.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class FeignClientConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public RequestInterceptor feignClientInterceptor() {
        return template -> {
            // 获取对象
            ServletRequestAttributes attribute = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attribute == null) {
                return;
            }
            // 获取请求对象
            HttpServletRequest request = attribute.getRequest();
            // 获取当前请求的header，获取到jwt令牌
            Enumeration<String> headerNames = request.getHeaderNames();
            if (headerNames == null) {
                return;
            }
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                String headerValue = request.getHeader(headerName);
                // 将header向下传递
                template.header(headerName, headerValue);
            }
        };
    }
}