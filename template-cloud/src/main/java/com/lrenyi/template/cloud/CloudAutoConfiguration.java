package com.lrenyi.template.cloud;

import java.net.URI;
import com.lrenyi.template.api.ApiAutoConfiguration;
import com.lrenyi.template.cloud.config.FeignClientConfiguration;
import com.lrenyi.template.cloud.config.FeignClientErrorDecoder;
import com.lrenyi.template.core.TemplateConfigProperties;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.SpringOpaqueTokenIntrospector;
import org.springframework.web.client.RestTemplate;

/**
 * 微服务/云侧自动配置：Feign、负载均衡等。
 * 当与 template-api 同时存在时优先注册「使用负载均衡 RestTemplate 的 OpaqueTokenIntrospector」，
 * 以便内省地址可写服务名（如 <a href="http://auth-service/oauth2/introspect">...</a>）。
 */
@ComponentScan
@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore(ApiAutoConfiguration.class)
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
    public ErrorDecoder errorDecoder() {
        return new FeignClientErrorDecoder();
    }

    @Bean
    @ConditionalOnProperty(name = "app.template.feign.retry.enabled", havingValue = "true")
    public Retryer feignRetryer(TemplateConfigProperties properties) {
        TemplateConfigProperties.RetryConfig retry = properties.getFeign().getRetry();
        return new Retryer.Default(retry.getPeriod(), retry.getMaxPeriod(), retry.getMaxAttempts());
    }
    
    /**
     * 使用负载均衡 RestTemplate 的 Opaque Token 内省器，便于内省地址配置为服务名。
     * 本配置在 ApiAutoConfiguration 之前执行，与 api 同时存在时以此实现为准。
     */
    @Bean
    @ConditionalOnProperty(
            name = "app.template.oauth2.opaque-token.enabled", havingValue = "true", matchIfMissing = true
    )
    public OpaqueTokenIntrospector opaqueTokenIntrospector(TemplateConfigProperties properties,
            @LoadBalanced RestTemplate restTemplate,
            ApiAutoConfiguration.SecurityAutoConfiguration securityAutoConfiguration) {
        TemplateConfigProperties.OAuth2Config oauth2 = properties.getOauth2();
        TemplateConfigProperties.OAuth2Config.OpaqueTokenConfig opaqueToken = oauth2.getOpaqueToken();
        String uri = opaqueToken.getIntrospectionUri();
        String clientId = opaqueToken.getClientId();
        String clientSecret = opaqueToken.getClientSecret();
        RestTemplate client = restTemplate;
        URI url = URI.create(uri);
        if (url.getPort() != -1) {
            client = new RestTemplate();
        }
        client.getInterceptors().add(new BasicAuthenticationInterceptor(clientId, clientSecret));
        SpringOpaqueTokenIntrospector introspector = new SpringOpaqueTokenIntrospector(uri, client);
        return securityAutoConfiguration.makeSpringOpaqueTokenIntrospector(introspector);
    }
}
