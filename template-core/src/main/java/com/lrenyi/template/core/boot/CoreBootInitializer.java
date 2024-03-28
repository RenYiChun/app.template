package com.lrenyi.template.core.boot;

import com.lrenyi.template.core.coder.ConfigDecryption;
import com.lrenyi.template.core.nats.event.EventInitService;
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

public class CoreBootInitializer extends ConfigDecryption implements
        ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {
    
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Map<String, Object> decryptedProperties = new HashMap<>();
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        MutablePropertySources propertySources = environment.getPropertySources();
        for (PropertySource<?> propertySource : propertySources) {
            if (!(propertySource instanceof EnumerablePropertySource<?> enumerablePropertySource)) {
                continue;
            }
            decryptionCommon(enumerablePropertySource, decryptedProperties);
        }
        
        if (!decryptedProperties.isEmpty()) {
            propertySources.remove(DECRYPTED_PROPERTY_SOURCE_NAME);
            propertySources.addFirst(new MapPropertySource(DECRYPTED_PROPERTY_SOURCE_NAME,
                                                           decryptedProperties
            ));
        }
        EventInitService.init();
    }
    
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
