package com.lrenyi.template.core.config.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

public interface JsonProcessor {
    String toJson(Object obj) throws JsonProcessingException;
    
    <T> T fromJson(String json, Class<T> type) throws JsonProcessingException;
    
    JsonNode parse(String json) throws JsonProcessingException;
    
    String prettyPrint(Object obj) throws JsonProcessingException;
    
    // 添加自定义类型适配器
    default <T> void registerTypeAdapter(Class<T> type, Object adapter) {
        // 默认空实现，具体处理器实现
    }
}
