package com.lrenyi.oauth2.service.config;

import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

@Slf4j
public class ConfigImportSelector implements ImportSelector {
    
    @Override
    public String[] selectImports(@NonNull AnnotationMetadata importingClassMetadata) {
        ClassLoader loader = ConfigImportSelector.class.getClassLoader();
        List<String> imports = new ArrayList<>();
        boolean present =
                ClassUtils.isPresent("org.springframework.data.redis.core.RedisTemplate", loader);
        if (present) {
            imports.add(RedisOauthServiceConfig.class.getName());
        }
        return imports.toArray(new String[0]);
    }
}
