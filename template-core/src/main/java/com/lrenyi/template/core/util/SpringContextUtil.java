package com.lrenyi.template.core.util;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SpringContextUtil implements ApplicationContextAware {
    @Getter
    private static ApplicationContext applicationContext = null;
    
    //通过class获取Bean.
    public static <T> T getBean(Class<T> clazz) {
        ApplicationContext applicationContext = getApplicationContext();
        if (applicationContext == null) {
            return null;
        }
        return applicationContext.getBean(clazz);
    }
    
    //通过name,以及Clazz返回指定的Bean
    public static <T> T getBean(String name, Class<T> clazz) {
        return getApplicationContext().getBean(name, clazz);
    }
    
    public static String getProperties(String key) {
        String property = null;
        try {
            property = getApplicationContext().getEnvironment().getProperty(key);
            if (property == null) {
                return null;
            }
            property = new String(property.getBytes(StandardCharsets.ISO_8859_1),
                                  StandardCharsets.UTF_8
            );
        } catch (Exception e) {
            log.error("", e);
        }
        return property;
    }
    
    public static <T> Map<String, T> getBeansOfType(Class<T> cls) {
        return getApplicationContext().getBeansOfType(cls);
    }
    
    @Override
    public void setApplicationContext(@NonNull ApplicationContext context) throws BeansException {
        if (SpringContextUtil.applicationContext == null) {
            SpringContextUtil.applicationContext = context;
        }
    }
}