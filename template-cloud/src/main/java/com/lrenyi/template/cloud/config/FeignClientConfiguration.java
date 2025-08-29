package com.lrenyi.template.cloud.config;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.util.TemplateConstant;
import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
public class FeignClientConfiguration {
    
    @Bean
    @ConditionalOnProperty(name = "app.template.feign.enabled", havingValue = "true", matchIfMissing = true)
    public RequestInterceptor requestInterceptor(TemplateConfigProperties templateConfigProperties) {
        TemplateConfigProperties.FeignProperties feign = templateConfigProperties.getFeign();
        List<String> headers = feign.getHeaders();
        return template -> {
            template.header(TemplateConstant.HEADER_NAME, "true");
            // 获取对象
            ServletRequestAttributes attribute = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attribute == null || headers == null || headers.isEmpty()) {
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
                if (headers.contains(headerName)) {
                    log.info("headerName:{} It will be automatically passed to the downstream service", headerName);
                    String headerValue = request.getHeader(headerName);
                    // 将header向下传递
                    template.header(headerName, headerValue);
                }
            }
        };
    }
}