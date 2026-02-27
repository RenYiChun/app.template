package com.lrenyi.oauth2.service.config;

import java.util.Map;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * 从 OAuth2 授权服务器的 {@link Authentication} 中提取用户名。
 * 使用 direct type cast 替代反射，由具备 authorization-server 依赖的 oauth2-service 模块提供。
 */
@Component
public class OAuth2PrincipalNameExtractor {
    
    /**
     * 尝试从 OAuth2 Access Token 类型的认证中提取用户名。
     *
     * @param authentication 当前请求的认证信息
     * @return 若能识别并提取则返回用户名，否则返回 empty
     */
    public Optional<String> extract(Authentication authentication) {
        if (authentication instanceof OAuth2AccessTokenAuthenticationToken tokenAuth) {
            Map<String, Object> params = tokenAuth.getAdditionalParameters();
            if (params != null) {
                Object username = params.get(OAuth2TokenIntrospectionClaimNames.USERNAME);
                if (username != null && !username.toString().isEmpty()) {
                    return Optional.of(username.toString());
                }
            }
        }
        return Optional.empty();
    }
}
