package com.lrenyi.template.web.websocket;

import org.springframework.web.socket.WebSocketHandler;

public interface TemplateWebSocketHandler extends WebSocketHandler {
    String path();
}
