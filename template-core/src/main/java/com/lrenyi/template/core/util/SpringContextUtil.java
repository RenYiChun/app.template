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
        ApplicationContext applicationContext = getApplicationContext();
        if (applicationContext == null) {
            return null;
        }
        return applicationContext.getBean(name, clazz);
    }
    
    public static String getProperties(String key) {
        String property = null;
        try {
            ApplicationContext context = getApplicationContext();
            if (context == null) {
                return null;
            }
            property = context.getEnvironment().getProperty(key);
        } catch (Exception e) {
            log.error("Failed to get property for key: {}", key, e);
        }
        return property;
    }
    
    public static <T> Map<String, T> getBeansOfType(Class<T> cls) {
        ApplicationContext applicationContext = getApplicationContext();
        if (applicationContext == null) {
            return Map.of();
        }
        return applicationContext.getBeansOfType(cls);
    }
    
    @Override
    public void setApplicationContext(@NonNull ApplicationContext context) throws BeansException {
        if (SpringContextUtil.applicationContext == null) {
            SpringContextUtil.applicationContext = context;
        }
    }
}