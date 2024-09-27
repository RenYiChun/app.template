package com.lrenyi.template.web.converter;

import com.alibaba.fastjson2.JSON;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.AbstractJsonHttpMessageConverter;
import org.springframework.lang.NonNull;

public class FastJsonHttpMessageConverter extends AbstractJsonHttpMessageConverter {
    
    @Override
    public boolean canWrite(Type type, Class<?> clazz, MediaType mediaType) {
        if (byte[].class.isAssignableFrom(clazz) || clazz.isPrimitive() || String.class.isAssignableFrom(clazz)) {
            return false;
        }
        return super.canWrite(type, clazz, mediaType);
    }
    
    @Override
    @NonNull
    protected Object readInternal(@NonNull Type resolvedType,
            @NonNull Reader reader) throws Exception {
        return JSON.parseObject(reader, resolvedType);
    }
    
    @Override
    protected void writeInternal(@NonNull Object object,
            Type type,
            @NonNull Writer writer) throws Exception {
        String jsonString = JSON.toJSONString(object);
        writer.write(jsonString);
    }
}
