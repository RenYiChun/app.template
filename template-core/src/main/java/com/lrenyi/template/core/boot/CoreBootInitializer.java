package com.lrenyi.template.core.boot;

import com.lrenyi.template.core.coder.DefaultTemplateEncryptService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

public class CoreBootInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {
    protected static final String DECRYPTED_PROPERTY_SOURCE_NAME = "decryptedProperties";
    
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Map<String, Object> decryptedProperties = new HashMap<>();
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        MutablePropertySources propertySources = environment.getPropertySources();
        for (PropertySource<?> propertySource : propertySources) {
            if (!(propertySource instanceof EnumerablePropertySource<?> enumerablePropertySource)) {
                continue;
            }
            for (String key : enumerablePropertySource.getPropertyNames()) {
                Object rawValue = enumerablePropertySource.getProperty(key);
                if (!(rawValue instanceof String encryptedValue)) {
                    continue;
                }
                if (encryptedValue.startsWith("aENC(") && encryptedValue.endsWith(")")) {
                    String decryptedValue = decryptValue(encryptedValue);
                    decryptedProperties.put(key, decryptedValue);
                }
            }
        }
        
        if (!decryptedProperties.isEmpty()) {
            propertySources.remove(DECRYPTED_PROPERTY_SOURCE_NAME);
            propertySources.addFirst(new MapPropertySource(DECRYPTED_PROPERTY_SOURCE_NAME, decryptedProperties));
        }
    }
    
    private String decryptValue(String encryptedValue) {
        String ciphertext = encryptedValue.substring(5, encryptedValue.length() - 1);
        return DefaultTemplateEncryptService.decodeStatic(ciphertext);
    }
    
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
