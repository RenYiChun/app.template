package com.lrenyi.template.core.boot;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import com.lrenyi.template.core.coder.DefaultTemplateEncryptService;
import org.jspecify.annotations.NonNull;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

/**
 * 按需解密的 PropertySource：在首次访问配置项时才解密 aENC(...) 值，减少启动时开销。
 * <p>
 * 作为链首注入，{@link #getProperty(String)} 被调用时从后续 source 获取原始值，
 * 若为 aENC(...) 则解密并缓存后返回；非加密值直接透传。
 * <p>
 * 使用 ThreadLocal 重入保护：当 configurationProperties 委托回本链时，返回 null 打破递归，
 * 从而可从 configurationProperties 获取扁平化配置（含 Nacos、application.yml 等），实现全量自动解密。
 */
final class LazyDecryptingPropertySource extends PropertySource<Object> {

    private static final String PREFIX = "aENC(";
    private static final String SUFFIX = ")";
    
    /** 重入保护：configurationProperties.getProperty 会委托回本链，需在重入时返回 null 打破循环 */
    private static final ThreadLocal<Boolean> IN_RESOLVE = ThreadLocal.withInitial(() -> false);

    private final MutablePropertySources propertySources;
    private final ConcurrentHashMap<String, String> decryptedCache = new ConcurrentHashMap<>();

    LazyDecryptingPropertySource(MutablePropertySources propertySources) {
        super(CoreBootInitializer.DECRYPTED_PROPERTY_SOURCE_NAME, new Object());
        this.propertySources = propertySources;
    }
    
    @Override
    public Object getProperty(@NonNull String name) {
        if (IN_RESOLVE.get()) {
            return null;
        }
        try {
            IN_RESOLVE.set(true);
            Object raw = resolveFromChain(name);
            if (raw == null) {
                return null;
            }
            if (!(raw instanceof String value)) {
                return raw;
            }
            if (!value.startsWith(PREFIX) || !value.endsWith(SUFFIX)) {
                return value;
            }
            return decryptedCache.computeIfAbsent(name, k -> decryptValue(value));
        } finally {
            IN_RESOLVE.set(false);
        }
    }
    
    private Object resolveFromChain(String name) {
        for (PropertySource<?> ps : propertySources) {
            if (ps != this) {
                Object value = ps.getProperty(name);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }
    
    private static String decryptValue(String encryptedValue) {
        String ciphertext = encryptedValue.substring(PREFIX.length(), encryptedValue.length() - SUFFIX.length());
        return DefaultTemplateEncryptService.decodeStatic(ciphertext);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        LazyDecryptingPropertySource other = (LazyDecryptingPropertySource) obj;
        return Objects.equals(getName(), other.getName()) && propertySources == other.propertySources;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(getName(), propertySources);
    }
}
