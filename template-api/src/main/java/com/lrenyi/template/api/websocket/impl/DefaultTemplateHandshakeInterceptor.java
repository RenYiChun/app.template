package com.lrenyi.template.api.websocket.impl;

import java.util.Map;
import com.lrenyi.template.api.websocket.TemplateHandshakeInterceptor;
import com.lrenyi.template.core.TemplateConfigProperties;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * WebSocket 握手时基于当前框架 OAuth2 的默认认证校验。
 * <p>
 * 优先从 header {@code Authorization: Bearer <token>} 取 token，其次（且配置允许时）从 query {@code access_token} 取。
 * 使用 OpaqueTokenIntrospector 或 JwtDecoder 校验，通过则将 Principal 放入 attributes，供
 * {@link TemplatePrincipalHandshakeHandler} 写入 Session。
 * </p>
 * <p>
 * Opaque Token 模式下框架会自动提供 Introspector；仅 JWT 模式时需在应用中提供 {@link JwtDecoder} Bean，否则握手将拒绝。
 * </p>
 * <p>
 * 生产环境建议使用 wss://，并通过 Header 传 token；可通过 {@code app.template.websocket.allow-token-in-query-parameter=false} 禁止从 query 取 token。
 * </p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.template.enabled", havingValue = "true", matchIfMissing = true)
public class DefaultTemplateHandshakeInterceptor implements TemplateHandshakeInterceptor {
    
    /** 握手属性中存放已认证 Principal 的 key，供 HandshakeHandler 使用 */
    public static final String PRINCIPAL_ATTRIBUTE = "org.springframework.security.authentication.Principal";
    
    private final ObjectProvider<OpaqueTokenIntrospector> opaqueTokenIntrospectorProvider;
    private final ObjectProvider<JwtDecoder> jwtDecoderProvider;
    private final TemplateConfigProperties templateConfigProperties;
    
    public DefaultTemplateHandshakeInterceptor(ObjectProvider<OpaqueTokenIntrospector> opaqueTokenIntrospectorProvider,
            ObjectProvider<JwtDecoder> jwtDecoderProvider,
            TemplateConfigProperties templateConfigProperties) {
        this.opaqueTokenIntrospectorProvider = opaqueTokenIntrospectorProvider;
        this.jwtDecoderProvider = jwtDecoderProvider;
        this.templateConfigProperties = templateConfigProperties;
    }
    
    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @NonNull Map<String, Object> attributes) {
        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            log.debug("WebSocket handshake: missing token");
            return false;
        }
        try {
            Object principal = validateTokenAndGetPrincipal(token);
            if (principal != null) {
                attributes.put(PRINCIPAL_ATTRIBUTE, principal);
                return true;
            }
        } catch (Exception e) {
            log.debug("WebSocket handshake: token validation failed", e);
            return false;
        }
        return false;
    }
    
    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            Exception ex) {
        // 无需收尾逻辑，Principal 已通过 attributes 传递
    }
    
    private String resolveToken(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        String auth = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(auth) && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        if (!isAllowTokenInQueryParameter()) {
            return null;
        }
        String query = request.getURI().getQuery();
        if (StringUtils.hasText(query)) {
            String value =
                    UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("access_token");
            if (StringUtils.hasText(value)) {
                log.debug("WebSocket token 来自 query 参数，生产环境建议使用 Authorization: Bearer 头");
                return value;
            }
        }
        return null;
    }

    private boolean isAllowTokenInQueryParameter() {
        return templateConfigProperties != null
                && templateConfigProperties.getWebsocket() != null
                && templateConfigProperties.getWebsocket().isAllowTokenInQueryParameter();
    }
    
    /** 校验 token 并返回 Principal（OAuth2AuthenticatedPrincipal 或 JwtAuthenticationToken），供 Session 使用 */
    private Object validateTokenAndGetPrincipal(String token) {
        OpaqueTokenIntrospector introspector = opaqueTokenIntrospectorProvider.getIfAvailable();
        if (introspector != null && isOpaqueTokenMode()) {
            return introspector.introspect(token);
        }
        JwtDecoder decoder = jwtDecoderProvider.getIfAvailable();
        if (decoder != null) {
            Jwt jwt = decoder.decode(token);
            return new JwtAuthenticationToken(jwt);
        }
        log.warn("WebSocket 认证：未找到 OpaqueTokenIntrospector 或 JwtDecoder Bean，请配置 OAuth2 资源服务器或提供自定义 "
                         + "TemplateHandshakeInterceptor");
        return null;
    }
    
    private boolean isOpaqueTokenMode() {
        //@formatter:off
        return templateConfigProperties != null
                && templateConfigProperties.getOauth2() != null
                && templateConfigProperties.getOauth2().getOpaqueToken() != null
                && templateConfigProperties.getOauth2().getOpaqueToken().isEnabled();
        //@formatter:on
    }
}
