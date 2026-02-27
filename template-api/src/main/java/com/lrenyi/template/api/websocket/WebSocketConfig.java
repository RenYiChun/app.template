package com.lrenyi.template.api.websocket;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.lrenyi.template.api.websocket.impl.TemplatePrincipalHandshakeHandler;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.util.SpringContextUtil;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 注册与握手配置。生产环境建议使用 wss:// 并在前端通过 Header 传递 token。
 */
@Slf4j
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    private TemplateConfigProperties templateConfigProperties;
    private TemplatePrincipalHandshakeHandler principalHandshakeHandler;
    
    @Autowired
    public void setTemplateConfigProperties(TemplateConfigProperties templateConfigProperties) {
        this.templateConfigProperties = templateConfigProperties;
    }
    
    @Autowired(required = false)
    public void setPrincipalHandshakeHandler(TemplatePrincipalHandshakeHandler principalHandshakeHandler) {
        this.principalHandshakeHandler = principalHandshakeHandler;
    }

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        Map<String, TemplateWebSocketHandler> handlers;
        handlers = SpringContextUtil.getBeansOfType(TemplateWebSocketHandler.class);
        if (handlers.isEmpty()) {
            return;
        }
        List<TemplateHandshakeInterceptor> interceptors = getHandshakeInterceptors();
        TemplateHandshakeInterceptor[] interceptorArray = interceptors.toArray(new TemplateHandshakeInterceptor[0]);
        List<String> allowedOriginPatterns = getAllowedOriginPatternsFromCorsConfig();
        for (TemplateWebSocketHandler handler : handlers.values()) {
            WebSocketHandlerRegistration registration = registry.addHandler(handler, handler.path());
            if (principalHandshakeHandler != null) {
                registration.setHandshakeHandler(principalHandshakeHandler);
            }
            if (!CollectionUtils.isEmpty(allowedOriginPatterns)) {
                registration.setAllowedOriginPatterns(allowedOriginPatterns.toArray(new String[0]));
            }
            if (interceptorArray.length > 0) {
                registration.addInterceptors(interceptorArray);
            }
        }
    }
    
    /**
     * 复用 app.template.security.cors 的允许源配置，与 HTTP CORS 一致；
     * 未启用或未配置时返回空列表，WebSocket 仅允许同源。
     */
    private List<String> getAllowedOriginPatternsFromCorsConfig() {
        if (templateConfigProperties == null) {
            return Collections.emptyList();
        }
        TemplateConfigProperties.CorsProperties cors = templateConfigProperties.getSecurity().getCors();
        if (cors == null || !cors.isEnabled() || CollectionUtils.isEmpty(cors.getAllowedOriginPatterns())) {
            return Collections.emptyList();
        }
        return cors.getAllowedOriginPatterns();
    }
    
    private List<TemplateHandshakeInterceptor> getHandshakeInterceptors() {
        Map<String, TemplateHandshakeInterceptor> map;
        map = SpringContextUtil.getBeansOfType(TemplateHandshakeInterceptor.class);
        return map.isEmpty() ? Collections.emptyList() : map.values().stream().toList();
    }
}