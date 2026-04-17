package com.lrenyi.template.core.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;

/**
 * In the config-data bootstrap phase, inject a chain-head decrypting PropertySource
 * so any aENC(...) value can be resolved before Nacos/bootstrap credentials are read.
 */
public class EarlyDecryptPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.remove(CoreBootInitializer.DECRYPTED_PROPERTY_SOURCE_NAME);
        propertySources.addFirst(new LazyDecryptingPropertySource(propertySources));
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER - 1;
    }
}
