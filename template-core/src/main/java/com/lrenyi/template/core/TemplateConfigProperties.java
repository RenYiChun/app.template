package com.lrenyi.template.core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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
public class TemplateConfigProperties {
    private boolean enabled = true;
    
    /**
     * OAuth2模块配置
     */
    @NestedConfigurationProperty
    private OAuth2Config oauth2 = new OAuth2Config();
    
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
        private AuthorizationServerConfig authorizationServer = new AuthorizationServerConfig();
        private PasswordGrantConfig passwordGrant = new PasswordGrantConfig();
        private JwtConfig jwt = new JwtConfig();
        @NestedConfigurationProperty
        private OpaqueTokenConfig opaqueToken = new OpaqueTokenConfig();
        private StorageConfig storage = new StorageConfig();
        private ClientManagementConfig clientManagement = new ClientManagementConfig();
        private EndpointsConfig endpoints = new EndpointsConfig();
        
        @Setter
        @Getter
        public static class AuthorizationServerConfig {
            private boolean enabled = true;
        }
        
        @Setter
        @Getter
        public static class PasswordGrantConfig {
            private boolean enabled = true;
        }
        
        @Setter
        @Getter
        public static class JwtConfig {
            private boolean enabled = true;
        }
        
        @Setter
        @Getter
        public static class OpaqueTokenConfig {
            private String introspectionUri = "http://127.0.0.1/opaque/token/check";
            private boolean enable = false;
            private String introspectionClientId = "default-client-id";
            private String introspectionClientSecret = "app.template";
        }
        
        @Setter
        @Getter
        public static class StorageConfig {
            private String type = "memory"; // memory or redis
        }
        
        @Setter
        @Getter
        public static class ClientManagementConfig {
            private boolean enabled = true;
        }
        
        @Setter
        @Getter
        public static class EndpointsConfig {
            private boolean enabled = true;
        }
    }
    
    /**
     * 安全配置
     */
    @Setter
    @Getter
    public static class SecurityProperties implements InitializingBean {
        private boolean enabled = true;
        private String securityKey = "default";
        private Set<String> allPermitUrls = new HashSet<>();
        private Set<String> defaultPermitUrls = new HashSet<>();
        private Map<String, Set<String>> permitUrls = new HashMap<>();
        private Set<String> resourcePermitUrls = new HashSet<>();
        private boolean autoRedirectLoginPage = false;
        private String redirectLoginPageUrl = "";
        private boolean localJwtPublicKey = true;
        private String netJwtPublicKeyDomain;
        private String customizeLoginPage;
        /**
         * AuthorizationService的类型，目前支持两种，memory, redis
         */
        private String authorizationType = "memory";
        
        @Override
        public void afterPropertiesSet() {
            defaultPermitUrls.addAll(Arrays.asList("/oauth2/token",
                                                   "/opaque/token/check",
                                                   "/jwt/public/key",
                                                   "/favicon",
                                                   "/static/**",
                                                   "/webjars/**"
            ));
            allPermitUrls.addAll(defaultPermitUrls);
            permitUrls.forEach((key, vales) -> allPermitUrls.addAll(vales));
            if (StringUtils.hasLength(customizeLoginPage)) {
                allPermitUrls.add(customizeLoginPage);
            }
            if (!resourcePermitUrls.isEmpty()) {
                allPermitUrls.addAll(resourcePermitUrls);
            }
        }
    }
}