package com.lrenyi.template.api.websocket;

import com.lrenyi.template.core.util.SpringContextUtil;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Slf4j
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
    
    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        Map<String, TemplateWebSocketHandler> beans = SpringContextUtil.getBeansOfType(TemplateWebSocketHandler.class);
        if (beans.isEmpty()) {
            return;
        }
        AtomicReference<WebSocketHandlerRegistration> handler = new AtomicReference<>();
        beans.forEach((key, value) -> {
            String path = value.path();
            handler.set(registry.addHandler(value, path));
        });
        WebSocketHandlerRegistration registration = handler.get();
        registration.setAllowedOrigins("*");
        Map<String, TemplateHandshakeInterceptor> interceptor = SpringContextUtil.getBeansOfType(
                TemplateHandshakeInterceptor.class);
        if (interceptor.isEmpty()) {
            return;
        }
        List<TemplateHandshakeInterceptor> list = interceptor.values().stream().toList();
        TemplateHandshakeInterceptor[] array = list.toArray(new TemplateHandshakeInterceptor[0]);
        registration.addInterceptors(array);
    }
}