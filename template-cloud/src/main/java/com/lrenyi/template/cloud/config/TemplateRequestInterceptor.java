package com.lrenyi.template.cloud.config;

import java.util.Enumeration;
import java.util.List;
import com.lrenyi.template.cloud.service.OauthUtilService;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.util.TemplateConstant;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
public class TemplateRequestInterceptor implements RequestInterceptor {
    private final TemplateConfigProperties templateConfigProperties;
    private final OauthUtilService oauthUtilService;
    
    public TemplateRequestInterceptor(TemplateConfigProperties templateConfigProperties,
                                      OauthUtilService oauthUtilService) {
        this.templateConfigProperties = templateConfigProperties;
        this.oauthUtilService = oauthUtilService;
    }
    
    @Override
    public void apply(RequestTemplate template) {
        TemplateConfigProperties.FeignProperties feign = templateConfigProperties.getFeign();
        template.header(TemplateConstant.HEADER_NAME, "true");
        if (feign.isNotOauth()) {
            return;
        }
        // 获取对象
        ServletRequestAttributes attribute = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attribute == null) {
            makeClientOauth(template);
            return;
        }
        List<String> headers = feign.getHeaders();
        if (headers == null || headers.isEmpty()) {
            return;
        }
        // 获取请求对象
        HttpServletRequest request = attribute.getRequest();
        // 获取当前请求的header，获取到jwt令牌
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames == null) {
            return;
        }
        boolean haveAuthorization = false;
        List<String> lowerHeader = headers.stream().map(String::toLowerCase).toList();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if ("authorization".equalsIgnoreCase(headerName)) {
                haveAuthorization = true;
            }
            if (lowerHeader.contains(headerName.toLowerCase())) {
                log.info("headerName:{} It will be automatically passed to the downstream service", headerName);
                String headerValue = request.getHeader(headerName);
                // 将header向下传递
                template.header(headerName, headerValue);
            }
        }
        if (!haveAuthorization) {
            makeClientOauth(template);
        }
    }
    
    private void makeClientOauth(RequestTemplate template) {
        boolean enabled = templateConfigProperties.getSecurity().isEnabled();
        if (!enabled) {
            return;
        }
        String token = oauthUtilService.fetchToken("server");
        template.header("Authorization", "Bearer " + token);
    }
}
