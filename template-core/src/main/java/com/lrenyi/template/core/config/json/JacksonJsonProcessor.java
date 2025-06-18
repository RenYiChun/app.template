package com.lrenyi.template.core.config.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(ObjectMapper.class)
public class JacksonJsonProcessor implements JsonProcessor {
    
    private final ObjectMapper objectMapper;
    
    public JacksonJsonProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public String toJson(Object obj) throws JsonProcessingException {
        return objectMapper.writeValueAsString(obj);
    }
    
    @Override
    public <T> T fromJson(String json, Class<T> type) throws JsonProcessingException {
        return objectMapper.readValue(json, type);
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
}
