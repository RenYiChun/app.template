package com.lrenyi.template.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.StringUtils;

/**
 * Template框架统一配置属性
 * 集中管理所有模块的配置开关
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "app.template")
public class TemplateConfigProperties implements InitializingBean {
    private boolean enabled = true;
    
    /**
     * OAuth2模块配置
     */
    @NestedConfigurationProperty
    private OAuth2Config oauth2 = new OAuth2Config();
    
    @NestedConfigurationProperty
    private AuthorizeConfig authorize = new AuthorizeConfig();
    
    /**
     * 安全配置
     */
    @NestedConfigurationProperty
    private SecurityProperties security = new SecurityProperties();
    
    /**
     * Web模块配置
     */
    @NestedConfigurationProperty
    private WebProperties web = new WebProperties();
    
    @NestedConfigurationProperty
    private FeignProperties feign = new FeignProperties();
    
    private AuditLogProperties audit = new AuditLogProperties();
    
    @Setter
    @Getter
    public static class AuthorizeConfig {
        private boolean enabled = true;
    }
    
    @Setter
    @Getter
    public static class AuditLogProperties {
        private boolean enabled = false;
        private List<String> oauth2Endpoints = Collections.singletonList("/oauth2/token");
    }
    
    @Setter
    @Getter
    public static class FeignProperties {
        private boolean enabled = true;
        private List<String> headers = new ArrayList<>();
        private String oauthClientId;
        private String oauthClientSecret;
    }
    
    /**
     * Web模块配置
     */
    @Setter
    @Getter
    public static class WebProperties {
        private String jsonProcessorType;
        private boolean exportExceptionDetail;
    }
    
    /**
     * OAuth2模块配置
     */
    @Setter
    @Getter
    public static class OAuth2Config {
        private boolean enabled = true;
        private boolean skipPreAuthentication;
        @NestedConfigurationProperty
        private OpaqueTokenConfig opaqueToken = new OpaqueTokenConfig();
        
        @Setter
        @Getter
        public static class OpaqueTokenConfig {
            private String introspectionUri = "http://127.0.0.1/oauth2/introspect";
            private boolean enabled = false;
            private String clientId = "default-client-id";
            private String clientSecret = "app.template";
        }
    }
    
    /**
     * 安全配置
     */
    @Setter
    @Getter
    public static class SecurityProperties {
        private boolean enabled = true;
        private String securityKey = "default";
        private Set<String> allPermitUrls = new HashSet<>();
        private Set<String> defaultPermitUrls = new HashSet<>();
        private Map<String, Set<String>> permitUrls = new HashMap<>();
        private Set<String> resourcePermitUrls = new HashSet<>();
        private boolean localJwtPublicKey = true;
        private String netJwtPublicKeyDomain;
        private String customizeLoginPage;
        private boolean sessionIdleTimeout = false;
        private Long sessionTimeOutSeconds;
        
        /**
         * AuthorizationService的类型，目前支持两种，memory, redis
         */
        private String authorizationType = "memory";
    }
    
    @Override
    public void afterPropertiesSet() {
        List<String> list = Arrays.asList("/jwt/public/key", "/favicon");
        security.defaultPermitUrls.addAll(list);
        security.allPermitUrls.addAll(security.defaultPermitUrls);
        security.permitUrls.forEach((key, vales) -> security.allPermitUrls.addAll(vales));
        if (StringUtils.hasLength(security.customizeLoginPage)) {
            security.allPermitUrls.add(security.customizeLoginPage);
        }
        if (!security.resourcePermitUrls.isEmpty()) {
            security.allPermitUrls.addAll(security.resourcePermitUrls);
        }
    }
}