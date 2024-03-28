package com.lrenyi.template.core.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(ExceptionConfig.PREFIX)
public class ExceptionConfig {
    public static final String PREFIX = "app.config.exception";
    
    private boolean export;
}
