package com.lrenyi.template.core.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON处理器工厂
 * 负责创建和管理不同类型的JSON处理器实例
 */
@Slf4j
public class JsonProcessorFactory {
    
    private static final Map<String, Supplier<JsonProcessor>> PROCESSOR_SUPPLIERS = new ConcurrentHashMap<>();
    private static final Map<String, JsonProcessor> PROCESSOR_CACHE = new ConcurrentHashMap<>();
    
    // 处理器类型常量
    public static final String JACKSON = "jackson";
    public static final String DEFAULT = JACKSON;
    
    static {
        // 注册默认的处理器供应商
        registerProcessorSupplier(JACKSON, () -> {
            ObjectMapper objectMapper = new ObjectMapper();
            return new JacksonJsonProcessor(objectMapper);
        });
    }
    
    /**
     * 注册JSON处理器供应商
     */
    public static void registerProcessorSupplier(String name, Supplier<JsonProcessor> supplier) {
        PROCESSOR_SUPPLIERS.put(name.toLowerCase(), supplier);
        log.info("Registered JSON processor supplier: {}", name);
    }
    
    /**
     * 创建JSON处理器实例
     */
    public static JsonProcessor createProcessor(String type) {
        String processorType = type != null ? type.toLowerCase() : DEFAULT;
        
        // 先从缓存中获取
        JsonProcessor cached = PROCESSOR_CACHE.get(processorType);
        if (cached != null) {
            return cached;
        }
        
        // 从供应商创建新实例
        Supplier<JsonProcessor> supplier = PROCESSOR_SUPPLIERS.get(processorType);
        if (supplier == null) {
            log.warn("Unknown JSON processor type: {}, falling back to default: {}", type, DEFAULT);
            supplier = PROCESSOR_SUPPLIERS.get(DEFAULT);
        }
        
        if (supplier == null) {
            throw new IllegalStateException("No JSON processor supplier found for type: " + processorType);
        }
        
        JsonProcessor processor = supplier.get();
        PROCESSOR_CACHE.put(processorType, processor);
        log.info("Created JSON processor: {} ({})", processor.getProcessorName(), processorType);
        
        return processor;
    }
    
    /**
     * 创建默认的JSON处理器
     */
    public static JsonProcessor createDefaultProcessor() {
        return createProcessor(DEFAULT);
    }
    
    /**
     * 创建Jackson处理器
     */
    public static JsonProcessor createJacksonProcessor() {
        return createProcessor(JACKSON);
    }
    
    /**
     * 创建Jackson处理器（使用自定义ObjectMapper）
     */
    public static JsonProcessor createJacksonProcessor(ObjectMapper objectMapper) {
        return new JacksonJsonProcessor(objectMapper);
    }
    
    /**
     * 获取所有已注册的处理器类型
     */
    public static String[] getAvailableProcessorTypes() {
        return PROCESSOR_SUPPLIERS.keySet().toArray(new String[0]);
    }
    
    /**
     * 检查是否支持指定的处理器类型
     */
    public static boolean isProcessorTypeSupported(String type) {
        return PROCESSOR_SUPPLIERS.containsKey(type.toLowerCase());
    }
    
    /**
     * 清除处理器缓存
     */
    public static void clearCache() {
        PROCESSOR_CACHE.clear();
        log.info("JSON processor cache cleared");
    }
    
    /**
     * 获取缓存的处理器数量
     */
    public static int getCacheSize() {
        return PROCESSOR_CACHE.size();
    }
}