package com.lrenyi.template.web.utils;

import com.lrenyi.template.web.converter.FastJsonHttpMessageConverter;
import java.util.Iterator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

@Slf4j
public class ConverterCustomizer {
    
    public static void replace(List<HttpMessageConverter<?>> converters) {
        boolean haveFastJson = false;
        Iterator<HttpMessageConverter<?>> iterator = converters.iterator();
        while (iterator.hasNext()) {
            HttpMessageConverter<?> converter = iterator.next();
            if (converter instanceof FastJsonHttpMessageConverter) {
                haveFastJson = true;
            }
            if (converter instanceof MappingJackson2HttpMessageConverter) {
                try {
                    iterator.remove();
                } catch (Throwable cause) {
                    log.warn("cant remove: {}, because: {}",
                             MappingJackson2HttpMessageConverter.class,
                             cause.getMessage()
                    );
                }
            }
        }
        if (!haveFastJson) {
            converters.add(new FastJsonHttpMessageConverter());
        }
    }
}
