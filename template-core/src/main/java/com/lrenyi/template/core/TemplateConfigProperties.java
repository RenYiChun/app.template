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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.StringUtils;

/**
 * Template框架统一配置属性
 * 集中管理所有模块的配置开关
 */
@Slf4j
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
    
    /**
     * 方法级安全配置（@PreAuthorize / @PostAuthorize / @Secured 等是否生效）
     */
    @NestedConfigurationProperty
    private MethodSecurityConfig methodSecurity = new MethodSecurityConfig();
    
    @NestedConfigurationProperty
    private Flow flow = new Flow();
    
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
    
    /**
     * WebSocket 相关配置
     */
    @NestedConfigurationProperty
    private WebSocketProperties websocket = new WebSocketProperties();
    
    private AuditLogProperties audit = new AuditLogProperties();
    
    @Override
    public void afterPropertiesSet() {
        List<String> list = Arrays.asList(security.getNetJwtPublicKeyPath(), "/favicon");
        security.defaultPermitUrls.addAll(list);
        security.allPermitUrls.addAll(security.defaultPermitUrls);
        security.permitUrls.forEach((key, vales) -> security.allPermitUrls.addAll(vales));
        if (StringUtils.hasLength(security.customizeLoginPage)) {
            security.allPermitUrls.add(security.customizeLoginPage);
        }
        if (!security.resourcePermitUrls.isEmpty()) {
            security.allPermitUrls.addAll(security.resourcePermitUrls);
        }
        validateConfig();
    }
    
    /**
     * 配置合理性校验，防止远程配置源（如 Nacos）覆盖导致行为异常。
     * 仅输出 WARN 日志，不阻断启动。
     */
    private void validateConfig() {
        if (flow.getConsumer().getConcurrencyLimit() <= 0) {
            log.warn("[配置校验] app.template.flow.consumer.concurrency-limit={} 不合法，"
                             + "可能被远程配置覆盖，将导致 Flow 引擎无法正常工作",
                     flow.getConsumer().getConcurrencyLimit()
            );
        }
        if (flow.getConsumer().getTtlMill() <= 0) {
            log.warn("[配置校验] app.template.flow.consumer.ttl-mill={} 不合法，" + "缓存数据将立即过期",
                     flow.getConsumer().getTtlMill()
            );
        }
        if (security.isEnabled() && !security.isLocalJwtPublicKey()
                && !StringUtils.hasLength(security.getNetJwtPublicKeyUri())
                && !StringUtils.hasLength(security.getNetJwtPublicKeyDomain())) {
            log.warn("[配置校验] 安全已启用但 JWT 配置为远程公钥模式，请设置 net-jwt-public-key-uri（完整 URI）或 net-jwt-public-key-domain（域名）");
        }
        log.info("[配置摘要] enabled={}, security.effective={}, flow.concurrencyLimit={}, "
                         + "feign.effective={}, oauth2.effective={}, audit.effective={}, methodSecurity.effective={}",
                 enabled,
                 isSecurityEffectivelyEnabled(),
                 flow.getConsumer().getConcurrencyLimit(),
                 isFeignEffectivelyEnabled(),
                 isOauth2EffectivelyEnabled(),
                 isAuditEffectivelyEnabled(),
                 isMethodSecurityEffectivelyEnabled()
        );
    }
    
    /**
     * 总开关关闭时，各功能均视为未启用。以下方法用于统一判断"有效启用"状态。
     */
    public boolean isSecurityEffectivelyEnabled() {
        return enabled && security.isEnabled();
    }
    
    public boolean isFeignEffectivelyEnabled() {
        return enabled && feign.isEnabled();
    }
    
    public boolean isOauth2EffectivelyEnabled() {
        return enabled && oauth2.isEnabled();
    }
    
    public boolean isAuditEffectivelyEnabled() {
        return enabled && audit.isEnabled();
    }
    
    public boolean isMethodSecurityEffectivelyEnabled() {
        return enabled && methodSecurity.isEnabled();
    }
    
    @Setter
    @Getter
    public static class MethodSecurityConfig {
        /** 是否启用方法级安全（@PreAuthorize 等注解生效） */
        private boolean enabled = true;
    }
    
    /**
     * Flow 配置
     */
    @Setter
    @Getter
    public static class Flow {
        /** 监控配置 */
        @NestedConfigurationProperty
        private Monitor monitor = new Monitor();
        /** 生产端配置 */
        @NestedConfigurationProperty
        private Producer producer = new Producer();
        /** 消费端配置 */
        @NestedConfigurationProperty
        private Consumer consumer = new Consumer();
    }
    
    @Setter
    @Getter
    public static class Monitor {}
    
    @Setter
    @Getter
    public static class Producer {
        /** 子流并行拉取数 (控制同时执行拉取任务的任务数) */
        private int parallelism = 40;
        /** 在途数据上限阈值 (控制系统中允许存在的在途数据条目数，≤0 表示用 1 倍全局消费许可数) */
        private int maxInFlightThreshold = 4000;
        /** 缓存最大容量 */
        private int maxCacheSize = 40000;
        /** 是否开启缓存 */
        private boolean cacheEnabled = true;
    }
    
    @Setter
    @Getter
    public static class Consumer {
        /** 缓存数据存活时间（毫秒） */
        private long ttlMill = 10000;
        /** 全局并发消费许可数阈值 */
        private int concurrencyLimit = 1000;
    }
    
    @Setter
    @Getter
    public static class AuditLogProperties {
        private boolean enabled = false;
        /** 需审计的 OAuth2 端点 URI 列表（与 oauth2AuditPathPrefix 二选一或同时生效） */
        private List<String> oauth2Endpoints = Collections.singletonList("/oauth2/token");
        /** 审计该路径前缀下的所有请求（如 /oauth2 表示 /oauth2/authorize、/oauth2/token、/oauth2/revoke 等全部审计），为空则仅按 oauth2Endpoints 列表 */
        private String oauth2AuditPathPrefix = "/oauth2";
    }
    
    @Setter
    @Getter
    public static class FeignProperties {
        private boolean enabled = true;
        private List<String> headers = new ArrayList<>();
        private boolean notOauth = true;
        private String oauthClientId;
        private String oauthClientSecret;
        /**
         * 当非空时，仅当请求来源 IP 匹配其中任一（CIDR 或单 IP）且带 X-Internal-Call: true 时才视为内部调用。
         * 用于防止客户端伪造 X-Internal-Call 绕过认证。示例：["127.0.0.1", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"]
         */
        private List<String> internalCallAllowedIpPatterns = new ArrayList<>();
        /** 重试配置（默认关闭） */
        @NestedConfigurationProperty
        private RetryConfig retry = new RetryConfig();
    }
    
    @Setter
    @Getter
    public static class RetryConfig {
        /** 是否启用 Feign 重试 */
        private boolean enabled = false;
        /** 最大重试次数（不含首次请求） */
        private int maxAttempts = 3;
        /** 初始重试间隔（毫秒） */
        private long period = 100;
        /** 最大重试间隔（毫秒） */
        private long maxPeriod = 1000;
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
     * WebSocket 配置
     */
    @Setter
    @Getter
    public static class WebSocketProperties {
        /**
         * 是否允许从 URL query 参数 {@code access_token} 解析 token。
         * 为 false 时仅从 Header {@code Authorization: Bearer <token>} 解析。
         * 生产环境建议设为 false，避免 token 进入日志、Referer 等。
         */
        private boolean allowTokenInQueryParameter = false;
    }
    
    /**
     * OAuth2模块配置
     */
    @Setter
    @Getter
    public static class OAuth2Config {
        private boolean enabled = true;
        private boolean skipPreAuthentication;
        private String tokenUrl;
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
        /**
         * 视为生产环境的 Spring Profile 列表，当激活其中任一 profile 且使用本地 JWT 公钥时，将拒绝启动。
         * 默认包含 prod、production。
         */
        private Set<String> productionProfiles = new HashSet<>(Arrays.asList("prod", "production"));
        /**
         * JWT 公钥 JWK Set 的完整 URI，优先级高于 domain+path。
         * 配置后直接使用，适用于 Keycloak、Auth0 等第三方 IdP（路径各异）。
         */
        private String netJwtPublicKeyUri;
        /**
         * JWT 公钥所在域名，与 net-jwt-public-key-path 拼接使用。
         * 当 net-jwt-public-key-uri 未配置时生效。
         */
        private String netJwtPublicKeyDomain;
        /**
         * JWT 公钥路径，与 net-jwt-public-key-domain 拼接。
         * 默认 /jwt/public/key，与 template-oauth2-service 的端点一致。
         */
        private String netJwtPublicKeyPath = "/jwt/public/key";
        private String customizeLoginPage;
        private boolean sessionIdleTimeout = false;
        private Long sessionTimeOutSeconds;
        private Long tokenMaxLifetimeSeconds = 24 * 3600L;
        
        /**
         * 授权信息存储方式：memory（默认）、redis、jdbc。
         * 对应配置项 app.template.security.authorization.store-type。
         * jdbc 时需配置数据源，启动时自动建表并从配置初始化客户端。
         */
        @NestedConfigurationProperty
        private AuthorizationProperties authorization = new AuthorizationProperties();
        
        /**
         * CORS 跨域配置。enabled=true 时由框架根据下方属性构建 CorsConfigurationSource 并注入 Security 链，
         * 无需再写 Java 代码；需完全自定义时可将 enabled 置为 false 并通过 httpConfigurerProvider 注入自己的 CORS。
         */
        @NestedConfigurationProperty
        private CorsProperties cors = new CorsProperties();
    }
    
    /**
     * 授权相关配置，对应 app.template.security.authorization.*
     */
    @Setter
    @Getter
    public static class AuthorizationProperties {
        /** 存储类型：memory、redis、jdbc */
        private String storeType = "memory";
    }
    
    /**
     * CORS 跨域配置项，对应 app.template.security.cors.*
     */
    @Setter
    @Getter
    public static class CorsProperties {
        private boolean enabled = false;
        /** 允许的源模式，如 <a href="http://localhost">localhost</a>:* ；默认本地开发常用值 */
        private List<String> allowedOriginPatterns =
                new ArrayList<>(Arrays.asList("http://localhost:*", "http://127.0.0.1:*"));
        private List<String> allowedMethods =
                new ArrayList<>(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        private List<String> allowedHeaders = new ArrayList<>(Collections.singletonList("*"));
        private Boolean allowCredentials = true;
        private Long maxAge = 3600L;
    }
}