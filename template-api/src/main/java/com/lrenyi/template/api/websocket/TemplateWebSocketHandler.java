package com.lrenyi.template.api.websocket;

import org.springframework.web.socket.WebSocketHandler;

public interface TemplateWebSocketHandler extends WebSocketHandler {
    String path();
}
