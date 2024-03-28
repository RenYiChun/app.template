package com.lrenyi.template.service.security;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
public class CustomFeignClientInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        try {
            // 获取对象
            ServletRequestAttributes attribute =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attribute == null) {
                return;
            }
            // 获取请求对象
            HttpServletRequest request = attribute.getRequest();
            // 获取当前请求的header，获取到jwt令牌
            Enumeration<String> headerNames = request.getHeaderNames();
            if (headerNames != null) {
                while (headerNames.hasMoreElements()) {
                    String headerName = headerNames.nextElement();
                    String headerValue = request.getHeader(headerName);
                    // 将header向下传递
                    template.header(headerName, headerValue);
                }
            }
        } catch (Exception e) {
            log.error("", e);
        }
    }
}
