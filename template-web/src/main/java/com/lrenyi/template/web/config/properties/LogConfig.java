package com.lrenyi.template.web.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(LogConfig.PREFIX)
public class LogConfig {
    public static final String PREFIX = "app.config.log";
    
    private boolean dataSave;
}
