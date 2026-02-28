package com.lrenyi.template.api.feign;

import java.util.List;
import com.lrenyi.template.core.util.TemplateConstant;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.CollectionUtils;

/**
 * 匹配「内部调用」请求：带 X-Internal-Call: true，且可选地要求来源 IP 在白名单内。
 * <p>
 * 仅当配置了 {@code app.template.feign.internal-call-allowed-ip-patterns} 时才会校验 IP，
 * 否则仅校验请求头。建议生产环境配置 IP 白名单，防止客户端伪造 X-Internal-Call 绕过认证。
 * </p>
 */
@Slf4j
public class InternalRequestMatcher implements RequestMatcher {
    
    private final List<String> allowedIpPatterns;
    
    public InternalRequestMatcher() {
        this(null);
    }
    
    /**
     * @param allowedIpPatterns 允许的 IP 或 CIDR（如 127.0.0.1、10.0.0.0/8），为空时仅校验请求头
     */
    public InternalRequestMatcher(List<String> allowedIpPatterns) {
        this.allowedIpPatterns = allowedIpPatterns;
        if (CollectionUtils.isEmpty(allowedIpPatterns)) {
            log.warn(
                    "[安全警告] InternalRequestMatcher 未配置 IP 白名单！仅依赖 X-Internal-Call 请求头是不安全的，容易被伪造。请配置 app.template"
                            + ".feign.internal-call-allowed-ip-patterns。");
        }
    }
    
    @Override
    public boolean matches(HttpServletRequest request) {
        if (!"true".equals(request.getHeader(TemplateConstant.HEADER_NAME))) {
            return false;
        }
        if (CollectionUtils.isEmpty(allowedIpPatterns)) {
            return true;
        }
        String remoteAddr = getRemoteAddr(request);
        for (String pattern : allowedIpPatterns) {
            if (new IpAddressMatcher(pattern.trim()).matches(remoteAddr)) {
                return true;
            }
        }
        return false;
    }
    
    private static String getRemoteAddr(HttpServletRequest request) {
        String addr = request.getRemoteAddr();
        return addr != null ? addr : "";
    }
}
