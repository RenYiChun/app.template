package com.lrenyi.template.core.boot;

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
 * 使用重入保护避免与 Spring Boot 的 ConfigurationPropertySourcesPropertySource 形成递归调用导致 StackOverflowError。
 */
final class LazyDecryptingPropertySource extends PropertySource<Object> {
    
    private static final String PREFIX = "aENC(";
    private static final String SUFFIX = ")";
    
    /** 需要跳过的 PropertySource 名称：其 getProperty 会委托回本链，导致无限递归 */
    private static final String CONFIGURATION_PROPERTIES_SOURCE_NAME = "configurationProperties";
    
    private final MutablePropertySources propertySources;
    private final ConcurrentHashMap<String, String> decryptedCache = new ConcurrentHashMap<>();
    
    LazyDecryptingPropertySource(MutablePropertySources propertySources) {
        super(CoreBootInitializer.DECRYPTED_PROPERTY_SOURCE_NAME, new Object());
        this.propertySources = propertySources;
    }
    
    @Override
    public Object getProperty(@NonNull String name) {
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
    }
    
    private Object resolveFromChain(String name) {
        for (PropertySource<?> ps : propertySources) {
            if (ps == this) {
                continue;
            }
            if (CONFIGURATION_PROPERTIES_SOURCE_NAME.equals(ps.getName())) {
                continue;
            }
            Object value = ps.getProperty(name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
    
    private static String decryptValue(String encryptedValue) {
        String ciphertext = encryptedValue.substring(PREFIX.length(), encryptedValue.length() - SUFFIX.length());
        return DefaultTemplateEncryptService.decodeStatic(ciphertext);
    }
}
