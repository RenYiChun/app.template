package com.lrenyi.template.api.websocket.impl;

import java.security.Principal;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * 从握手阶段由 {@link com.lrenyi.template.api.websocket.impl.DefaultTemplateHandshakeInterceptor} 写入的 attributes 中取出
 * Principal，
 * 作为 WebSocket Session 的用户身份，便于 Handler 内通过 {@code session.getPrincipal()} 做鉴权或业务逻辑。
 */
public class TemplatePrincipalHandshakeHandler extends DefaultHandshakeHandler {
    
    @Override
    protected Principal determineUser(@NonNull ServerHttpRequest request,
            @NonNull WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        Object principal = attributes.get(DefaultTemplateHandshakeInterceptor.PRINCIPAL_ATTRIBUTE);
        return principal instanceof Principal ? (Principal) principal : null;
    }
}
