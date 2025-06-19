package com.lrenyi.template.core.config.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.lrenyi.template.core.config.properties.AppJsonConfigProperties;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * JSON服务类
 * 提供统一的JSON处理接口，支持多种JSON处理器
 */
@Getter
@Slf4j
@Service
public class JsonService {
    
    /**
     * 获取底层的JSON处理器实例
     */
    private final JsonProcessor processor;
    /**
     * 获取配置的处理器类型
     */
    private final String processorType;
    
    public JsonService(JsonProcessor processor, AppJsonConfigProperties properties) {
        this.processor = processor;
        this.processorType = properties.getProcessorType();
        log.info("JsonService initialized with processor: {} ({})", processor.getProcessorName(), processorType);
    }
    
    /**
     * 序列化对象为JSON字符串
     */
    public String serialize(Object obj) {
        try {
            return processor.toJson(obj);
        } catch (Exception e) {
            log.error("JSON serialization failed for object: {}", obj.getClass().getSimpleName(), e);
            throw new RuntimeException("JSON serialization failed", e);
        }
    }
    
    /**
     * 反序列化JSON字符串为指定类型对象
     */
    public <T> T deserialize(String json, Class<T> type) {
        try {
            return processor.fromJson(json, type);
        } catch (Exception e) {
            log.error("JSON deserialization failed for type: {}", type.getSimpleName(), e);
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }
    
    /**
     * 反序列化JSON字符串为复杂类型对象（支持泛型）
     */
    public <T> T deserialize(String json, TypeReference<T> typeReference) {
        try {
            return processor.fromJson(json, typeReference);
        } catch (Exception e) {
            log.error("JSON deserialization failed for TypeReference: {}", typeReference.getType(), e);
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }
    
    /**
     * 解析JSON字符串为JsonNode
     */
    public JsonNode parseToNode(String json) {
        try {
            return processor.parse(json);
        } catch (Exception e) {
            log.error("JSON parsing to JsonNode failed", e);
            throw new RuntimeException("JSON parsing failed", e);
        }
    }
    
    /**
     * 格式化输出JSON字符串
     */
    public String prettyPrint(Object obj) {
        try {
            return processor.prettyPrint(obj);
        } catch (Exception e) {
            log.error("JSON pretty printing failed for object: {}", obj.getClass().getSimpleName(), e);
            throw new RuntimeException("JSON pretty printing failed", e);
        }
    }
    
    /**
     * 将JSON字符串转换为Map
     */
    public Map<String, Object> toMap(String json) {
        try {
            return processor.toMap(json);
        } catch (Exception e) {
            log.error("JSON to Map conversion failed", e);
            throw new RuntimeException("JSON to Map conversion failed", e);
        }
    }
    
    /**
     * 将JSON字符串转换为List
     */
    public <T> List<T> toList(String json, Class<T> elementType) {
        try {
            return processor.toList(json, elementType);
        } catch (Exception e) {
            log.error("JSON to List conversion failed for element type: {}", elementType.getSimpleName(), e);
            throw new RuntimeException("JSON to List conversion failed", e);
        }
    }
    
    /**
     * 注册自定义类型适配器
     */
    public void registerCustomAdapter(Class<?> type, Object adapter) {
        try {
            processor.registerTypeAdapter(type, adapter);
            log.info("Registered custom adapter for type: {}", type.getSimpleName());
        } catch (Exception e) {
            log.error("Failed to register custom adapter for type: {}", type.getSimpleName(), e);
            throw new RuntimeException("Failed to register custom adapter", e);
        }
    }
    
    /**
     * 获取当前使用的JSON处理器名称
     */
    public String getProcessorName() {
        return processor.getProcessorName();
    }
    
    /**
     * 检查是否支持某个特性
     */
    public boolean supportsFeature(JsonProcessor.JsonProcessorFeature feature) {
        return processor.supportsFeature(feature);
    }
    
}
