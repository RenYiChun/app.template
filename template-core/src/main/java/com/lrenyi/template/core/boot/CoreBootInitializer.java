package com.lrenyi.template.core.boot;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;

/**
 * 在 Environment 准备阶段注入按需解密的 PropertySource，对 aENC(...) 包装的配置项在首次访问时才解密，
 * 减少启动时全量遍历与解密的开销。
 */
public class CoreBootInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {
    protected static final String DECRYPTED_PROPERTY_SOURCE_NAME = "decryptedProperties";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        MutablePropertySources propertySources = environment.getPropertySources();
        propertySources.remove(DECRYPTED_PROPERTY_SOURCE_NAME);
        propertySources.addFirst(new LazyDecryptingPropertySource(propertySources));
    }
    
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
