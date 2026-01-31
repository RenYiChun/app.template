package com.lrenyi.template.core.flow.storage;

import com.lrenyi.template.core.flow.config.FlowStorageType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;

/**
 * Flow 存储工厂加载器
 * 使用 Java SPI 机制加载和发现存储工厂实现
 */
@Slf4j
public class FlowStorageFactoryLoader {
    
    private static volatile List<FlowStorageFactory> factories;
    private static final ConcurrentMap<FlowStorageType, FlowStorageFactory> factoryCache = new ConcurrentHashMap<>();
    
    /**
     * 加载所有存储工厂
     */
    private static List<FlowStorageFactory> loadFactories() {
        List<FlowStorageFactory> list = new ArrayList<>();
        try {
            ServiceLoader<FlowStorageFactory> loader = ServiceLoader.load(FlowStorageFactory.class);
            for (FlowStorageFactory factory : loader) {
                list.add(factory);
                log.debug("加载存储工厂: {} (类型: {}, 优先级: {})",
                          factory.getClass().getName(),
                          factory.getSupportedType(),
                          factory.getPriority()
                );
            }
            // 按优先级排序
            list.sort(Comparator.comparingInt(FlowStorageFactory::getPriority));
            log.info("共加载 {} 个存储工厂", list.size());
        } catch (Exception e) {
            log.error("加载存储工厂失败", e);
        }
        return Collections.unmodifiableList(list);
    }
    
    /**
     * 获取所有已加载的工厂
     */
    public static List<FlowStorageFactory> getFactories() {
        if (factories == null) {
            synchronized (FlowStorageFactoryLoader.class) {
                if (factories == null) {
                    factories = loadFactories();
                }
            }
        }
        return factories;
    }
    
    /**
     * 查找支持指定类型的工厂
     *
     * @param type 存储类型
     *
     * @return 工厂实例，如果未找到则返回 null
     */
    public static FlowStorageFactory findFactory(FlowStorageType type) {
        // 先查缓存
        FlowStorageFactory cached = factoryCache.get(type);
        if (cached != null) {
            return cached;
        }
        
        // 查找工厂
        FlowStorageFactory factory = getFactories().stream().filter(f -> f.supports(type)).findFirst().orElse(null);
        
        if (factory != null) {
            factoryCache.put(type, factory);
            log.debug("为类型 {} 找到工厂: {}", type, factory.getClass().getName());
        } else {
            log.warn("未找到支持类型 {} 的存储工厂", type);
        }
        
        return factory;
    }
    
    /**
     * 清除缓存（用于测试或重新加载）
     */
    public static void clearCache() {
        factoryCache.clear();
        synchronized (FlowStorageFactoryLoader.class) {
            factories = null;
        }
    }
}
