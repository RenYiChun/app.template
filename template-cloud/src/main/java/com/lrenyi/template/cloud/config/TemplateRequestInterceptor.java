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

/**
 * Feign 请求拦截：打标内部调用、透传指定 Header、无用户上下文时用 Client 凭证。
 * <p>
 * 安全注意：
 * <ul>
 *   <li>app.template.feign.headers 仅配置可信且需透传的 Header（如 Authorization），避免透传客户端可控且下游信任的 Header 导致越权。</li>
 *   <li>无 RequestContext 时使用 Client 凭证，下游收到的是服务身份而非用户身份，需按需区分。</li>
 *   <li>X-Internal-Call 由本拦截器设置，下游若据此放行需配合 app.template.feign.internal-call-allowed-ip-patterns 防伪造。</li>
 * </ul>
 * </p>
 */
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
                log.debug("headerName:{} passed to downstream", headerName);
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
