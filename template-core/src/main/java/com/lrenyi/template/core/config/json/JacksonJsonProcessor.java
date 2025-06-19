package com.lrenyi.template.core.config.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.util.List;
import java.util.Map;

/**
 * Jackson实现的JSON处理器
 * 基于Jackson库提供完整的JSON处理功能
 *
 * @param objectMapper -- GETTER --
 *                     获取底层的ObjectMapper实例，用于高级定制
 */
public record JacksonJsonProcessor(ObjectMapper objectMapper) implements JsonProcessor {
    
    private static final String PROCESSOR_NAME = "Jackson";
    
    @Override
    public String toJson(Object obj) throws JsonProcessingException {
        return objectMapper.writeValueAsString(obj);
    }
    
    @Override
    public <T> T fromJson(String json, Class<T> type) throws JsonProcessingException {
        return objectMapper.readValue(json, type);
    }
    
    @Override
    public <T> T fromJson(String json, TypeReference<T> typeReference) throws JsonProcessingException {
        return objectMapper.readValue(json, typeReference);
    }
    
    @Override
    public JsonNode parse(String json) throws JsonProcessingException {
        return objectMapper.readTree(json);
    }
    
    @Override
    public String prettyPrint(Object obj) throws JsonProcessingException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
    }
    
    @Override
    public Map<String, Object> toMap(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }
    
    @Override
    public <T> List<T> toList(String json, Class<T> elementType) throws JsonProcessingException {
        CollectionType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
        return objectMapper.readValue(json, listType);
    }
    
    @Override
    public <T> void registerTypeAdapter(Class<T> type, Object adapter) {
        if (adapter instanceof JsonSerializer || adapter instanceof JsonDeserializer) {
            SimpleModule module = new SimpleModule();
            if (adapter instanceof JsonSerializer jsonSerializer) {
                module.addSerializer(type, jsonSerializer);
            }
            if (adapter instanceof JsonDeserializer jsonDeserializer) {
                module.addDeserializer(type, jsonDeserializer);
            }
            objectMapper.registerModule(module);
        }
    }
    
    @Override
    public String getProcessorName() {
        return PROCESSOR_NAME;
    }
    
    @Override
    public boolean supportsFeature(JsonProcessorFeature feature) {
        return switch (feature) {
            case PRETTY_PRINT, CUSTOM_SERIALIZERS, CUSTOM_DESERIALIZERS, TYPE_ADAPTERS, STREAMING -> true;
            case VALIDATION -> false; // Jackson本身不提供JSON Schema验证
        };
    }
}
