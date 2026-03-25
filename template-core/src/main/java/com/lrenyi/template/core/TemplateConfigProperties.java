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
import org.jspecify.annotations.NonNull;
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
        String faviconPath = StringUtils.hasText(security.getFaviconPath()) ? security.getFaviconPath() :
                SecurityProperties.DEFAULT_FAVICON_PATH;
        List<String> list = Arrays.asList(security.getNetJwtPublicKeyPath(), faviconPath);
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
     * Flow limits 校验失败时抛出 IllegalArgumentException，应用启动中止。
     */
    private void validateConfig() {
        Flow.Limits limits = flow.getLimits();
        Flow.Global global = limits.getGlobal();
        Flow.PerJob perJob = getPerJob(limits, global);
        if (perJob.getQueuePollIntervalMill() <= 0) {
            throw new IllegalArgumentException("flow.limits.per-job.queue-poll-interval-mill 必须 > 0，当前值: "
                                                       + perJob.getQueuePollIntervalMill());
        }
        Flow.KeyedCache keyedCache = judgmentPerJobConfig(perJob);
        if (keyedCache.isMultiValueEnabled() && keyedCache.getMultiValueMaxPerKey() <= 0) {
            throw new IllegalArgumentException("flow.limits.per-job.multi-value-max-per-key 必须 > 0，当前值: "
                                                       + keyedCache.getMultiValueMaxPerKey());
        }
        if (keyedCache.isMultiValueEnabled()) {
            long maxEntries = (long) perJob.getStorageCapacity() * keyedCache.getEffectiveMultiValueMaxPerKey();
            log.info("[配置摘要] flow.multiValueEnabled=true, multiValueMaxPerKey={}, 预估最大 entry 数={}",
                     keyedCache.getEffectiveMultiValueMaxPerKey(),
                     maxEntries
            );
        }
        if (security.isEnabled() && !security.isLocalJwtPublicKey()
                && !StringUtils.hasLength(security.getNetJwtPublicKeyUri())
                && !StringUtils.hasLength(security.getNetJwtPublicKeyDomain())) {
            log.warn("[配置校验] 安全已启用但 JWT 配置为远程公钥模式，请设置 net-jwt-public-key-uri（完整 URI）或 net-jwt-public-key-domain（域名）");
        }
        log.info("[配置摘要] enabled={}, security.effective={}, flow.consumerThreads={}, "
                         + "feign.effective={}, oauth2.effective={}, audit.effective={}, methodSecurity.effective={}",
                 enabled,
                 isSecurityEffectivelyEnabled(), global.getConsumerThreads(),
                 isFeignEffectivelyEnabled(),
                 isOauth2EffectivelyEnabled(),
                 isAuditEffectivelyEnabled(),
                 isMethodSecurityEffectivelyEnabled()
        );
    }

    private static Flow.@NonNull KeyedCache judgmentPerJobConfig(Flow.PerJob perJob) {
        Flow.KeyedCache keyedCache = perJob.getKeyedCache();
        if (keyedCache.isMustMatchRetryEnabled() && keyedCache.getMustMatchRetryMaxTimes() < 1) {
            throw new IllegalArgumentException("flow.limits.per-job.must-match-retry-max-times 必须 >= 1，当前值: "
                                                       + keyedCache.getMustMatchRetryMaxTimes());
        }
        if (keyedCache.getMustMatchRetryBackoffMill() < 0) {
            throw new IllegalArgumentException("flow.limits.per-job.must-match-retry-backoff-mill 必须 >= 0，当前值: "
                                                       + keyedCache.getMustMatchRetryBackoffMill());
        }
        return keyedCache;
    }

    private Flow.@NonNull PerJob getPerJob(Flow.Limits limits, Flow.Global global) {
        Flow.PerJob perJob = getJob(limits, global);
        Flow.KeyedCache keyedCache = judgmentJobConfig(perJob);
        if (keyedCache.getEvictionBatchSize() <= 0) {
            throw new IllegalArgumentException(
                    "flow.limits.per-job.eviction-batch-size 必须 > 0，当前值: " + keyedCache.getEvictionBatchSize());
        }
        if (flow.getProducerBackpressureBlockingMode() == Flow.BackpressureBlockingMode.BLOCK_WITH_TIMEOUT
                && flow.getProducerBackpressureTimeoutMill() <= 0) {
            throw new IllegalArgumentException("flow.producer-backpressure-timeout-mill 必须 > 0，当前值: "
                                                       + flow.getProducerBackpressureTimeoutMill());
        }
        if (flow.getConsumerAcquireBlockingMode() == Flow.BackpressureBlockingMode.BLOCK_WITH_TIMEOUT
                && flow.getConsumerAcquireTimeoutMill() <= 0) {
            throw new IllegalArgumentException(
                    "flow.consumer-acquire-timeout-mill 必须 > 0，当前值: " + flow.getConsumerAcquireTimeoutMill());
        }
        return perJob;
    }

    private static Flow.@NonNull KeyedCache judgmentJobConfig(Flow.PerJob perJob) {
        Flow.KeyedCache keyedCache = judgmentConfig(perJob);
        if (keyedCache.getExpiryDeferInitialMill() <= 0) {
            throw new IllegalArgumentException("flow.limits.per-job.expiry-defer-initial-mill 必须 > 0，当前值: "
                                                       + keyedCache.getExpiryDeferInitialMill());
        }
        if (keyedCache.getExpiryDeferMaxMill() < keyedCache.getExpiryDeferInitialMill()) {
            throw new IllegalArgumentException(
                    "flow.limits.per-job.expiry-defer-max-mill 必须 >= expiry-defer-initial-mill，当前值: "
                            + keyedCache.getExpiryDeferMaxMill());
        }
        return keyedCache;
    }

    private static Flow.@NonNull KeyedCache judgmentConfig(Flow.PerJob perJob) {
        Flow.KeyedCache keyedCache = perJob.getKeyedCache();
        int effectivePending = perJob.getEffectivePendingConsumer();
        if (effectivePending <= 0) {
            throw new IllegalArgumentException(
                    "flow.limits.per-job.in-flight-consumer 或 flow.limits.per-job.consumer-threads 必须 > 0，"
                            + "当前 in-flight-consumer=" + perJob.getInFlightConsumer() + ", consumer-threads="
                            + perJob.getConsumerThreads());
        }
        if (keyedCache.getCacheTtlMill() <= 0) {
            throw new IllegalArgumentException(
                    "flow.limits.per-job.cache-ttl-mill 必须 > 0，当前值: " + keyedCache.getCacheTtlMill());
        }
        return keyedCache;
    }

    private static Flow.@NonNull PerJob getJob(Flow.Limits limits, Flow.Global global) {
        Flow.PerJob perJob = judgmentValidate(limits, global);
        if (perJob.getInFlightProduction() <= 0) {
            throw new IllegalArgumentException(
                    "flow.limits.per-job.in-flight-production 必须 > 0，当前值: " + perJob.getInFlightProduction());
        }
        if (perJob.getStorageCapacity() <= 0) {
            throw new IllegalArgumentException(
                    "flow.limits.per-job.storage-capacity 必须 > 0，当前值: " + perJob.getStorageCapacity());
        }
        return perJob;
    }

    private static Flow.@NonNull PerJob judgmentValidate(Flow.Limits limits, Flow.Global global) {
        Flow.PerJob perJob = limits.getPerJob();

        if (global.getConsumerThreads() <= 0 && perJob.getConsumerThreads() <= 0) {
            throw new IllegalArgumentException(
                    "flow.limits.global.consumer-threads 或 flow.limits.per-job.consumer-threads 至少一个必须 > 0，"
                            + "当前 global=" + global.getConsumerThreads() + ", per-job="
                            + perJob.getConsumerThreads());
        }
        if (perJob.getProducerThreads() <= 0) {
            throw new IllegalArgumentException(
                    "flow.limits.per-job.producer-threads 必须 > 0，当前值: " + perJob.getProducerThreads());
        }
        return perJob;
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
        /**
         * 背压阻塞模式：控制生产/消费在背压场景下是无限等待还是带超时等待。
         */
        public enum BackpressureBlockingMode {
            /** 一直阻塞直到条件满足，仅能被 Job 停止打断。 */
            BLOCK_FOREVER,
            /** 背压等待超过配置的超时时间后抛出超时异常。 */
            BLOCK_WITH_TIMEOUT
        }

        /**
         * 生产端发生背压时的阻塞模式。
         */
        private BackpressureBlockingMode producerBackpressureBlockingMode = BackpressureBlockingMode.BLOCK_WITH_TIMEOUT;

        /**
         * 生产端背压时允许的最长等待时间（毫秒），仅在 BLOCK_WITH_TIMEOUT 模式下生效。
         */
        private long producerBackpressureTimeoutMill = 30_000L;

        /**
         * 消费端获取消费许可时的阻塞模式。
         */
        private BackpressureBlockingMode consumerAcquireBlockingMode = BackpressureBlockingMode.BLOCK_WITH_TIMEOUT;

        /**
         * 消费端获取消费许可时允许的最长等待时间（毫秒），仅在 BLOCK_WITH_TIMEOUT 模式下生效。
         */
        private long consumerAcquireTimeoutMill = 30_000L;

        private boolean showStatus;

        /** 限流配置（全局与按 Job 分离） */
        @NestedConfigurationProperty
        private Limits limits = new Limits();

        /** 便捷方法：获取 limits，若为 null 则返回默认 */
        public Limits getLimits() {
            return limits != null ? limits : new Limits();
        }

        @Setter
        @Getter
        public static class Limits {
            /** 全主机级限制 */
            @NestedConfigurationProperty
            private Global global = new Global();
            /** 每 Job 独立限制 */
            @NestedConfigurationProperty
            private PerJob perJob = new PerJob();

            public Global getGlobal() {
                return global != null ? global : new Global();
            }

            public PerJob getPerJob() {
                return perJob != null ? perJob : new PerJob();
            }
        }

        @Setter
        @Getter
        public static class Global {
            /** 是否对全局信号量采用公平调度（FIFO），true 防饥饿、false 更高吞吐 */
            private boolean fairScheduling = true;
            /** 全主机生产线程数上限（producer-threads.global.limit），<=0 表示不限制 */
            private int producerThreads = 0;
            /** 全主机生产在途数据条数上限（in-flight.global.limit），<=0 表示不限制 */
            private int inFlightProduction = 0;
            /** 全主机存储条数上限（storage-capacity.global.limit），<=0 表示不限制 */
            private int storageCapacity = 0;
            /** 全主机关联消费线程数上限（consumer-threads.global.limit），<=0 表示不限制 */
            private int consumerThreads = 0;
            /** 全主机已离库未终结条数上限（in-flight-consumer.global.limit），<=0 表示不限制 */
            private int inFlightConsumer = 0;
            /**
             * 全主机 Sink 终端并发上限（sink-consumer-threads.global.limit），<=0 表示不限制。
             * 仅作用于管道终端 Sink 用户回调，与 consumer-threads 独立。
             */
            private int sinkConsumerThreads = 64;
            /** 驱逐协调线程数，默认单线程 */
            private int evictionCoordinatorThreads = 1;
            /** 驱逐扫描间隔（毫秒），0 表示使用 take() 阻塞等待，>0 表示 poll(timeout) 定期唤醒以检查关闭 */
            private long evictionScanIntervalMill = 0;
        }

        @Setter
        @Getter
        public static class PerJob {
            /** 每 Job 生产线程数（必须 >0） */
            private int producerThreads = 40;
            /** 每 Job 生产在途数据量（必须 >0） */
            private int inFlightProduction = 4000;
            /** 每 Job 关联消费线程数（必须 >0） */
            private int consumerThreads = 1000;
            /** 每 Job 已离库未终结条数上限（>0 显式值，0 表示使用 per-job.consumer-threads） */
            private int inFlightConsumer = 0;
            /** 每 Job 存储条数上限（必须 >0，适用于所有存储类型） */
            private int storageCapacity = 40000;
            /** 每 Job Queue 轮询间隔（毫秒，必须 >0） */
            private long queuePollIntervalMill = 10000;
            /** 每 Job 驱逐协调线程数，0 表示使用 global.eviction-coordinator-threads */
            private int evictionCoordinatorThreads = 0;
            /** 每 Job 驱逐扫描间隔（毫秒），0 表示使用 global.eviction-scan-interval-mill（仅非 DelayQueue 实现时生效） */
            private long evictionScanIntervalMill = 0;
            /** pending slot 获取超时时是否仍“提交 anyway” */
            private boolean strictPendingConsumerSlot = true;

            /** 带 key 的缓存：多值、超时与驱逐等统一在此配置（app.template.flow.limits.per-job.keyed-cache.*） */
            @NestedConfigurationProperty
            private KeyedCache keyedCache = new KeyedCache();

            /** 有效背压阈值：inFlightConsumer>0 时取该值，否则取 per-job.consumer-threads */
            public int getEffectivePendingConsumer() {
                return inFlightConsumer > 0 ? inFlightConsumer : consumerThreads;
            }

            /** 有效驱逐协调线程数：per-job > 0 时取 per-job，否则取 global。 */
            public int getEffectiveEvictionCoordinatorThreads(Flow.Global global) {
                return evictionCoordinatorThreads > 0 ? evictionCoordinatorThreads :
                        (global != null ? Math.max(1, global.getEvictionCoordinatorThreads()) : 1);
            }

            /** 有效驱逐扫描间隔：per-job > 0 时取 per-job，否则取 global；0 表示 take() 阻塞，>0 表示 poll(timeout)。 */
            public long getEffectiveEvictionScanIntervalMill(Flow.Global global) {
                return evictionScanIntervalMill > 0 ? evictionScanIntervalMill :
                        (global != null ? global.getEvictionScanIntervalMill() : 0);
            }

            public KeyedCache getKeyedCache() {
                return keyedCache != null ? keyedCache : new KeyedCache();
            }

            /** 配对模式：是否启用多对匹配（委托至 keyed-cache 配置） */
            public boolean isPairingMultiMatchEnabled() {
                return getKeyedCache().getPairingMultiMatchEnabled();
            }
        }

        /**
         * 带 key 的缓存配置：同 key 多 value、超时与驱逐等归为一类。
         * 配置前缀：app.template.flow.limits.per-job.keyed-cache.*
         */
        @Setter
        @Getter
        public static class KeyedCache {
            /** 同 key 多 value：是否开启（默认 false） */
            private boolean multiValueEnabled = false;
            /** 同 key 多 value：单 key 最大 value 数（开启后建议 16） */
            private int multiValueMaxPerKey = 1;
            /** 同 key 多 value：超限策略 DROP_OLDEST / DROP_NEWEST */
            private Flow.MultiValueOverflowPolicy multiValueOverflowPolicy = Flow.MultiValueOverflowPolicy.DROP_OLDEST;

            /** 缓存 TTL（毫秒，必须 >0） */
            private long cacheTtlMill = 10000L;
            /** 首次延期时长（毫秒） */
            private long expiryDeferInitialMill = 100L;
            /** 单次最大延期时长（毫秒） */
            private long expiryDeferMaxMill = 1000L;
            /** 延期退避倍数 */
            private double expiryDeferBackoffMultiplier = 2.0D;
            /** 单次协调扫描处理的最多 entry 数 */
            private int evictionBatchSize = 128;
            /** 存储容量是否按 entry 计数 */
            private boolean storageCountByEntry = true;

            /** 仅配对模式：是否启用强制配对重入 */
            private boolean mustMatchRetryEnabled = false;
            /** 仅配对模式：可重入最大次数（启用时必须 >=1） */
            private int mustMatchRetryMaxTimes = 3;
            /** 仅配对模式：每次重入前回退等待（毫秒，必须 >=0） */
            private long mustMatchRetryBackoffMill = 0;
            /** 配对模式：是否启用多对匹配；false 时配对成功后清空槽位内剩余条目并立即驱逐 */
            @Getter(lombok.AccessLevel.NONE)
            private boolean pairingMultiMatchEnabled = false;

            /** 配对模式：是否启用多对匹配 */
            public boolean getPairingMultiMatchEnabled() {
                return pairingMultiMatchEnabled;
            }

            /** 有效单 key 最大 value 数：multiValueEnabled=false 时恒为 1 */
            public int getEffectiveMultiValueMaxPerKey() {
                return multiValueEnabled ? Math.max(1, multiValueMaxPerKey) : 1;
            }

            /** 有效超时时长（毫秒），<=0 时回退到 cacheTtlMill */
            public long getEffectiveTimeoutMill() {
                return cacheTtlMill > 0 ? cacheTtlMill : 10_000L;
            }
        }

        /** 同 key 多 value 超限策略 */
        public enum MultiValueOverflowPolicy {
            /** 超限时淘汰最老项 */
            DROP_OLDEST,
            /** 超限时丢弃新入项 */
            DROP_NEWEST
        }
    }

    @Setter
    @Getter
    public static class AuditLogProperties {
        /** OAuth2 Token 端点默认路径，可通过 app.template.audit.oauth2-endpoints 自定义。 */
        public static final String DEFAULT_OAUTH2_TOKEN_ENDPOINT = "/oauth2/token";
        /** OAuth2 审计路径前缀默认值，可通过 app.template.audit.oauth2-audit-path-prefix 自定义。 */
        public static final String DEFAULT_OAUTH2_AUDIT_PATH_PREFIX = "/oauth2";

        private boolean enabled = false;
        /** 需审计的 OAuth2 端点 URI 列表（与 oauth2AuditPathPrefix 二选一或同时生效） */
        private List<String> oauth2Endpoints = Collections.singletonList(DEFAULT_OAUTH2_TOKEN_ENDPOINT);
        /** 审计该路径前缀下的所有请求（如 /oauth2 表示 /oauth2/authorize、/oauth2/token、/oauth2/revoke 等全部审计），为空则仅按 oauth2Endpoints 列表 */
        private String oauth2AuditPathPrefix = DEFAULT_OAUTH2_AUDIT_PATH_PREFIX;
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
            /** Opaque Token 内省 URI 默认值，可通过 app.template.oauth2.opaque-token.introspection-uri 自定义。 */
            public static final String DEFAULT_INTROSPECTION_URI = "http://127.0.0.1/oauth2/introspect";

            private String introspectionUri = DEFAULT_INTROSPECTION_URI;
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
        /** JWT 公钥路径默认值（与 application.properties 一致），可通过 app.template.security.net-jwt-public-key-path 自定义。 */
        public static final String DEFAULT_NET_JWT_PUBLIC_KEY_PATH = "/jwt/public/key"; //NOSONAR
        /** Favicon 路径默认值，可通过 app.template.security.favicon-path 自定义。 */
        public static final String DEFAULT_FAVICON_PATH = "/favicon";

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
        private String netJwtPublicKeyPath = DEFAULT_NET_JWT_PUBLIC_KEY_PATH;
        /**
         * Favicon 路径，加入默认放行列表。可通过 app.template.security.favicon-path 自定义。
         */
        private String faviconPath = DEFAULT_FAVICON_PATH;
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
