package com.lrenyi.template.core.config.properties;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Data
@Component
@ConfigurationProperties(CustomSecurityConfigProperties.PREFIX)
public class CustomSecurityConfigProperties implements InitializingBean {
    public static final String PREFIX = "app.config.security";
    private boolean enable = true;
    private Set<String> allPermitUrls = new HashSet<>();
    private Set<String> defaultPermitUrls = new HashSet<>();
    private Set<String> permitUrls = new HashSet<>();
    private Set<String> resourcePermitUrls = new HashSet<>();
    private boolean autoRedirectLoginPage = false;
    private String redirectLoginPageUrl = "";
    private boolean localJwtPublicKey = true;
    private String netJwtPublicKeyDomain;
    private String customizeLoginPage;
    private String defaultPasswordEncoderKey = "default";
    
    @NestedConfigurationProperty
    private OpaqueToken opaqueToken = new OpaqueToken();
    
    /**
     * AuthorizationService的类型，目前支持两种，memory, redis
     */
    private String authorizationType = "memory";
    
    @Override
    public void afterPropertiesSet() {
        allPermitUrls.addAll(defaultPermitUrls);
        allPermitUrls.addAll(Arrays.asList("/oauth2/token", "/login", "/opaque/token/check", "/jwt/public/key"));
        if (!permitUrls.isEmpty()) {
            allPermitUrls.addAll(permitUrls);
        }
        if (StringUtils.hasLength(customizeLoginPage)) {
            allPermitUrls.add(customizeLoginPage);
        }
        if (!resourcePermitUrls.isEmpty()) {
            allPermitUrls.addAll(resourcePermitUrls);
            allPermitUrls.addAll(Arrays.asList("/favicon", "/static/**", "/webjars/**"));
        }
    }
    
    @Getter
    @Setter
    public static class OpaqueToken {
        private String introspectionUri = "http://127.0.0.1/opaque/token/check";
        private boolean enable = false;
        private String introspectionClientId = "default-client-id";
        private String introspectionClientSecret = "app.template";
    }
}
