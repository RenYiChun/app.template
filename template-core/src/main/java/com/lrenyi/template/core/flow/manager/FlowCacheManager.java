package com.lrenyi.template.core.flow.manager;

import com.github.benmanes.caffeine.cache.Cache;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.flow.FlowJoiner;
import com.lrenyi.template.core.flow.ProgressTracker;
import com.lrenyi.template.core.flow.config.FlowStorageType;
import com.lrenyi.template.core.flow.impl.FlowFinalizer;
import com.lrenyi.template.core.flow.storage.CaffeineFlowStorage;
import com.lrenyi.template.core.flow.storage.FlowStorage;
import com.lrenyi.template.core.flow.storage.QueueFlowStorage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局受控缓存管理器
 * 职责：管理所有 Job 的缓存实例，并确保参数调整时资源能正确释放
 */
@Slf4j
public class FlowCacheManager {
    private final Map<String, FlowStorage<?>> storageRegistry = new ConcurrentHashMap<>();
    // 注册表：Key 为 uniqueKey (cacheName + dataType)
    private final Map<String, Cache<?, ?>> cacheRegistry = new ConcurrentHashMap<>();
    
    @SuppressWarnings("unchecked")
    public <T> FlowStorage<T> getOrCreateStorage(String jobId,
                                                 FlowJoiner<T> joiner,
                                                 TemplateConfigProperties.JobConfig config,
                                                 FlowFinalizer<T> finalizer,
                                                 ProgressTracker progressTracker) {
        FlowStorageType type = joiner.getStorageType();
        String uniqueKey = jobId + ":" + type.name();
        
        return (FlowStorage<T>) storageRegistry.computeIfAbsent(uniqueKey, k -> {
            int maxCacheSize = config.getMaxCacheSize();
            if (type == FlowStorageType.QUEUE) {
                return new QueueFlowStorage<>(maxCacheSize);
            }
            long ttlSeconds = config.getTtlSeconds();
            return new CaffeineFlowStorage<>(maxCacheSize, ttlSeconds, joiner, finalizer, progressTracker);
        });
    }
    
    /**
     * 强行使某个 Job 的缓存失效（用于 Job 关闭或配置刷新）
     */
    public void invalidate(String cacheName, Class<?> dataType) {
        String uniqueKey = cacheName + ":" + dataType.getName();
        Cache<?, ?> cache = cacheRegistry.remove(uniqueKey);
        if (cache != null) {
            // 这会触发上面 removalListener 中的 try-with-resources，释放信号量
            cache.invalidateAll();
            cache.cleanUp();
        }
    }
    
    /**
     * 系统停机清理
     */
    public void invalidateAll() {
        cacheRegistry.keySet().forEach(key -> {
            Cache<?, ?> cache = cacheRegistry.remove(key);
            if (cache != null) {
                cache.invalidateAll();
            }
        });
    }
}