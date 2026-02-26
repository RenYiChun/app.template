package com.lrenyi.template.api.websocket;

import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

/**
 * 基于路径注册的 WebSocket 处理器扩展接口。
 * <p>
 * 握手阶段已通过 {@link TemplateHandshakeInterceptor} 校验 token 并将 Principal 写入 Session，
 * 在 {@link #handleMessage(WebSocketSession, org.springframework.web.socket.WebSocketMessage) handleMessage} 等
 * 方法中可根据 {@code session.getPrincipal()} 及 authorities 做消息级/操作级鉴权，仅允许有权限的用户执行敏感操作。
 * </p>
 */
public interface TemplateWebSocketHandler extends WebSocketHandler {
    String path();
}
