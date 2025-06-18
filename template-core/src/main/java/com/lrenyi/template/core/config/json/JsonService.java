package com.lrenyi.template.core.config.json;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JsonService {
    
    private final JsonProcessor processor;
    
    public JsonService(@Lazy JsonProcessor processor) {
        this.processor = processor;
    }
    
    public String serialize(Object obj) {
        try {
            return processor.toJson(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public <T> T deserialize(String json, Class<T> type) {
        try {
            return processor.fromJson(json, type);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public void registerCustomAdapter(Class<?> type, Object adapter) {
        processor.registerTypeAdapter(type, adapter);
    }
}
