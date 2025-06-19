package com.lrenyi.template.core.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(AppJsonConfigProperties.PREFIX)
public class AppJsonConfigProperties {
    public static final String PREFIX = "app.config.json";
    
    private String processorType;
    
}
