package com.lrenyi.template.core.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(CustomRedisProperties.PREFIX)
public class CustomRedisProperties {
    public static final String PREFIX = "app.config.redis";
    
    private String keyPrefix = "app";
}
