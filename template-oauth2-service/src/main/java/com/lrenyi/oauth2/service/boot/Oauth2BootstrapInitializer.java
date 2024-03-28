package com.lrenyi.oauth2.service.boot;

import org.springframework.boot.BootstrapRegistry;
import org.springframework.boot.BootstrapRegistryInitializer;
import org.springframework.security.oauth2.core.http.converter.TemplateInitOauth;

public class Oauth2BootstrapInitializer implements BootstrapRegistryInitializer {
    
    @Override
    public void initialize(BootstrapRegistry registry) {
        TemplateInitOauth.init();
    }
}
