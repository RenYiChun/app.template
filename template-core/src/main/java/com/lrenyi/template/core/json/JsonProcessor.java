package com.lrenyi.template.core.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/**
 * 可扩展的JSON处理器接口
 * 支持多种JSON库的实现，提供统一的API
 */
public interface JsonProcessor {
    
    /**
     * 将对象序列化为JSON字符串
     */
    String toJson(Object obj) throws JsonProcessingException;
    
    /**
     * 将JSON字符串反序列化为指定类型的对象
     */
    <T> T fromJson(String json, Class<T> type) throws JsonProcessingException;
    
    /**
     * 将JSON字符串反序列化为复杂类型（支持泛型）
     */
    <T> T fromJson(String json, TypeReference<T> typeReference) throws JsonProcessingException;
    
    /**
     * 解析JSON字符串为JsonNode
     */
    JsonNode parse(String json) throws JsonProcessingException;
    
    /**
     * 格式化输出JSON字符串
     */
    String prettyPrint(Object obj) throws JsonProcessingException;
    
    /**
     * 将JSON字符串转换为Map
     */
    Map<String, Object> toMap(String json) throws JsonProcessingException;
    
    /**
     * 将JSON字符串转换为List
     */
    <T> List<T> toList(String json, Class<T> elementType) throws JsonProcessingException;
    
    /**
     * 注册自定义类型适配器
     */
    <T> void registerTypeAdapter(Class<T> type, Object adapter);
    
    /**
     * 获取处理器名称，用于标识不同的实现
     */
    String getProcessorName();
    
    /**
     * 检查是否支持某个特性
     */
    default boolean supportsFeature(JsonProcessorFeature feature) {
        return false;
    }
    
    /**
     * JSON处理器支持的特性枚举
     */
    enum JsonProcessorFeature {
        PRETTY_PRINT,
        CUSTOM_SERIALIZERS,
        CUSTOM_DESERIALIZERS,
        TYPE_ADAPTERS,
        STREAMING,
        VALIDATION
    }
}
