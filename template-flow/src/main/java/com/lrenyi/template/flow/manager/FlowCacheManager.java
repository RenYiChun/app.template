package com.lrenyi.template.flow.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.internal.FlowEgressHandler;
import com.lrenyi.template.flow.internal.FlowFinalizer;
import com.lrenyi.template.flow.model.FlowStorageType;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import com.lrenyi.template.flow.storage.FlowStorage;
import com.lrenyi.template.flow.util.FlowLogHelper;
import com.lrenyi.template.flow.storage.FlowStorageFactory;
import com.lrenyi.template.flow.storage.FlowStorageFactoryLoader;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局受控缓存管理器
 * 职责：管理所有 Job 的缓存实例，并确保参数调整时资源能正确释放
 */
@Slf4j
public class FlowCacheManager {
    private final Map<String, FlowStorage<?>> storageRegistry = new ConcurrentHashMap<>();
    private final FlowResourceRegistry resourceRegistry;

    /**
     * @param resourceRegistry 全局资源注册表，用于获取存储出口执行器
     */
    public FlowCacheManager(FlowResourceRegistry resourceRegistry) {
        this.resourceRegistry = resourceRegistry;
    }

    /**
     * 与 {@link #getOrCreateStorage} / Job 停止时清理逻辑共用的注册表键（含可选的消费节拍后缀）。
     */
    public static String buildStorageRegistryKey(String jobId, FlowJoiner<?> joiner) {
        FlowStorageType type = joiner.getStorageType();
        String base = jobId + ":" + type.name() + ":" + joiner.getDataType().getSimpleName();
        return joiner.storageConsumerTickIntervalMillis().isPresent()
                ? base + ":tick:" + joiner.storageConsumerTickIntervalMillis().getAsLong()
                : base;
    }

    @SuppressWarnings("unchecked")
    public <T> FlowStorage<T> getOrCreateStorage(String jobId,
        FlowJoiner<T> joiner,
        TemplateConfigProperties.Flow config,
        FlowFinalizer<T> finalizer,
        ProgressTracker progressTracker,
        FlowEgressHandler<T> egressHandler) {
        String uniqueKey = buildStorageRegistryKey(jobId, joiner);
        FlowStorageType type = joiner.getStorageType();

        Function<String, FlowStorage<?>> storageFunction = k -> {
            FlowStorageFactory factory = FlowStorageFactoryLoader.findFactory(type);
            if (factory == null) {
                throw new IllegalArgumentException("未找到支持类型 " + type + " 的存储工厂");
            }
            return factory.createStorage(jobId,
                                         joiner,
                                         config,
                                         finalizer,
                                         progressTracker,
                                         egressHandler,
                                         resourceRegistry,
                                         resourceRegistry.getMeterRegistry()
            );
        };
        return (FlowStorage<T>) storageRegistry.computeIfAbsent(uniqueKey, storageFunction);
    }

    /**
     * 按 {@link #buildStorageRegistryKey} 使对应缓存失效并 shutdown（用于 Job 停止）。
     */
    public void invalidateForJoiner(String jobId, String displayName, FlowJoiner<?> joiner) {
        String uniqueKey = buildStorageRegistryKey(jobId, joiner);
        FlowStorageType type = joiner.getStorageType();
        FlowStorage<?> storage = storageRegistry.remove(uniqueKey);
        if (storage != null) {
            storage.shutdown();
            log.debug("FlowStorage invalidated for {}, type={}", FlowLogHelper.formatJobContext(jobId, displayName),
                    type);
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
