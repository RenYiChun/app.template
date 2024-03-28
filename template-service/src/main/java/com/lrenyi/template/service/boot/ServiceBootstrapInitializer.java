package com.lrenyi.template.service.boot;

import org.springframework.boot.BootstrapRegistry;
import org.springframework.boot.BootstrapRegistryInitializer;

public class ServiceBootstrapInitializer implements BootstrapRegistryInitializer {
    @Override
    public void initialize(BootstrapRegistry registry) {
        System.setProperty("nacos.logging.config", "classpath:logback.xml");
    }
}
