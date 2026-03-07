package com.lrenyi.oauth2.service.config;

import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

public class ConfigImportSelector implements ImportSelector {
    
    @Override
    public String[] selectImports(@NonNull AnnotationMetadata importingClassMetadata) {
        ClassLoader loader = ConfigImportSelector.class.getClassLoader();
        List<String> imports = new ArrayList<>();
        if (ClassUtils.isPresent("org.springframework.data.redis.core.RedisTemplate", loader)) {
            imports.add(RedisOauthServiceConfig.class.getName());
        }
        if (ClassUtils.isPresent("org.springframework.jdbc.core.JdbcOperations", loader)) {
            imports.add(JdbcOauthServiceConfig.class.getName());
        }
        return imports.toArray(new String[0]);
    }
}
