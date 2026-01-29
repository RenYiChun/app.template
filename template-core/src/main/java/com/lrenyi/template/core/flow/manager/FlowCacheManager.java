package com.lrenyi.template.core.flow.manager;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.flow.FlowJoiner;
import com.lrenyi.template.core.flow.ProgressTracker;
import com.lrenyi.template.core.flow.config.FlowStorageType;
import com.lrenyi.template.core.flow.impl.FlowFinalizer;
import com.lrenyi.template.core.flow.storage.CaffeineFlowStorage;
import com.lrenyi.template.core.flow.storage.FlowStorage;
import com.lrenyi.template.core.flow.storage.QueueFlowStorage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局受控缓存管理器
 * 职责：管理所有 Job 的缓存实例，并确保参数调整时资源能正确释放
 */
@Slf4j
public class FlowCacheManager {
    private final Map<String, FlowStorage<?>> storageRegistry = new ConcurrentHashMap<>();
    private final Executor removalExecutor;

    /**
     * @param removalExecutor Caffeine removalListener 专用 executor，避免与 ForkJoinPool.commonPool() 争用，提升出缓存并发
     */
    public FlowCacheManager(Executor removalExecutor) {
        this.removalExecutor = removalExecutor;
    }

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
            return new CaffeineFlowStorage<>(maxCacheSize, ttlSeconds, joiner, finalizer, progressTracker, removalExecutor);
        });
    }

    /**
     * 按 jobId + 存储类型使对应缓存失效并 shutdown，与 getOrCreateStorage 的 key 约定一致（用于 Job 强制停止）
     */
    public void invalidateByJobId(String jobId, FlowStorageType type) {
        String uniqueKey = jobId + ":" + type.name();
        FlowStorage<?> storage = storageRegistry.remove(uniqueKey);
        if (storage != null) {
            storage.shutdown();
            log.debug("FlowStorage invalidated for jobId={}, type={}", jobId, type);
        }
    }

    /**
     * 强行使某个 Job 的缓存失效（按 cacheName:dataType 查找）
     * @deprecated 与 getOrCreateStorage 的 key 约定不一致，对 flow 模块请使用 {@link #invalidateByJobId(String, FlowStorageType)}
     */
    @Deprecated
    public void invalidate(String cacheName, Class<?> dataType) {
        String uniqueKey = cacheName + ":" + dataType.getName();
        FlowStorage<?> storage = storageRegistry.remove(uniqueKey);
        if (storage != null) {
            storage.shutdown();
        }
    }
    
    /**
     * 系统停机清理
     */
    public void invalidateAll() {
        Map<String, FlowStorage<?>> snapshot = new HashMap<>(storageRegistry);
        storageRegistry.clear();
        snapshot.forEach((key, storage) -> {
            try {
                storage.shutdown();
            } catch (Exception e) {
                log.warn("FlowStorage shutdown failed for key={}", key, e);
            }
        });
    }
}