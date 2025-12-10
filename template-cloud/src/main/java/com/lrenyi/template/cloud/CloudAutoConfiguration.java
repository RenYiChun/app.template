package com.lrenyi.template.cloud;

import com.lrenyi.template.api.ApiAutoConfiguration;
import com.lrenyi.template.cloud.config.FeignClientConfiguration;
import com.lrenyi.template.core.TemplateConfigProperties;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.security.oauth2.server.resource.introspection.SpringOpaqueTokenIntrospector;
import org.springframework.web.client.RestTemplate;

@ComponentScan
@Configuration(proxyBeanMethods = false)
@Import({CloudAutoConfiguration.FeignAutoConfiguration.class})
public class CloudAutoConfiguration {
    
    /**
     * Feign配置模块 - 条件导入
     */
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    @ConditionalOnProperty(name = "app.template.feign.enabled", havingValue = "true", matchIfMissing = true)
    @Import(FeignClientConfiguration.class)
    static class FeignAutoConfiguration {
        // 空的配置类，仅用于条件导入
    }
    
    @Bean
    @ConditionalOnProperty(
            name = "app.template.oauth2.opaque-token.enabled", havingValue = "true", matchIfMissing = true
    )
    public SpringOpaqueTokenIntrospector opaqueTokenIntrospector(TemplateConfigProperties properties,
                                                                 @LoadBalanced RestTemplate restTemplate,
                                                                 ApiAutoConfiguration.SecurityAutoConfiguration securityAutoConfiguration) {
        
        TemplateConfigProperties.OAuth2Config oauth2 = properties.getOauth2();
        TemplateConfigProperties.OAuth2Config.OpaqueTokenConfig opaqueToken = oauth2.getOpaqueToken();
        String uri = opaqueToken.getIntrospectionUri();
        String clientId = opaqueToken.getClientId();
        String clientSecret = opaqueToken.getClientSecret();
        URI url = URI.create(uri);
        int port = url.getPort();
        if (port != -1) {
            //配置带端口信息的url便于开发时的调试
            restTemplate = new RestTemplate();
        }
        restTemplate.getInterceptors().add(new BasicAuthenticationInterceptor(clientId, clientSecret));
        SpringOpaqueTokenIntrospector introspector = new SpringOpaqueTokenIntrospector(uri, restTemplate);
        return securityAutoConfiguration.makeSpringOpaqueTokenIntrospector(introspector);
    }
}
