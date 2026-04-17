package com.lrenyi.template.core.boot;

import java.util.LinkedHashMap;
import java.util.Map;
import com.lrenyi.template.core.coder.DefaultTemplateEncryptService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class EarlyDecryptPostProcessorTest {

    @Test
    void decryptsDynamicPropertiesWithoutHardcodedKeys() {
        ConfigurableEnvironment environment = new StandardEnvironment();
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("custom.bootstrap.secret", "aENC({RSA2048}cipher-1)");
        environment.getPropertySources().addFirst(new MapPropertySource("test", source));

        EarlyDecryptPostProcessor postProcessor = new EarlyDecryptPostProcessor();

        try (MockedStatic<DefaultTemplateEncryptService> mocked = Mockito.mockStatic(DefaultTemplateEncryptService.class)) {
            mocked.when(() -> DefaultTemplateEncryptService.decodeStatic("{RSA2048}cipher-1")).thenReturn("plain-1");
            mocked.when(() -> DefaultTemplateEncryptService.decodeStatic("{RSA2048}cipher-2")).thenReturn("plain-2");

            postProcessor.postProcessEnvironment(environment, new SpringApplication(Object.class));

            Assertions.assertEquals("plain-1", environment.getProperty("custom.bootstrap.secret"));
            Assertions.assertEquals("plain-1", environment.getProperty("custom.bootstrap.secret"));
            mocked.verify(() -> DefaultTemplateEncryptService.decodeStatic("{RSA2048}cipher-1"), Mockito.times(1));

            source.put("custom.bootstrap.secret", "aENC({RSA2048}cipher-2)");

            Assertions.assertEquals("plain-2", environment.getProperty("custom.bootstrap.secret"));
            mocked.verify(() -> DefaultTemplateEncryptService.decodeStatic("{RSA2048}cipher-2"), Mockito.times(1));
        }
    }
}
