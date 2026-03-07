package com.lrenyi.template.api.websocket;

import com.lrenyi.template.api.websocket.impl.TemplatePrincipalHandshakeHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * WebSocket 相关 Bean 定义。与 WebSocketConfig 分离，避免 WebSocketConfig 自引用导致循环依赖。
 */
@Configuration(proxyBeanMethods = false)
public class WebSocketBeansConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public TemplatePrincipalHandshakeHandler templatePrincipalHandshakeHandler() {
        return new TemplatePrincipalHandshakeHandler();
    }
}
