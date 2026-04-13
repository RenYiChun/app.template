package com.lrenyi.template.api.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionAuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.SpringOpaqueTokenIntrospector;

public final class OpaqueTokenIntrospectorUtils {

    private OpaqueTokenIntrospectorUtils() {
        // utility class
    }

    /**
     * 配置 Opaque Token 内省结果到 Spring Security 认证主体的转换逻辑。
     * 将内省响应中的 scope 转为 GrantedAuthority，供资源服务器侧 @PreAuthorize("hasAuthority('xxx')") 等使用。
     */
    public static SpringOpaqueTokenIntrospector customize(SpringOpaqueTokenIntrospector introspector) {
        introspector.setAuthenticationConverter(accessor -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            List<String> scopes = accessor.getScopes();
            if (scopes != null) {
                for (String scope : scopes) {
                    authorities.add(new SimpleGrantedAuthority(scope));
                }
            }
            return new OAuth2IntrospectionAuthenticatedPrincipal(accessor.getClaims(), authorities);
        });
        return introspector;
    }
}
