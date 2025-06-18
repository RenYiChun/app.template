package com.lrenyi.template.core.config;

import com.lrenyi.template.core.config.redis.RedisConfig;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

public class CoreConfigImportSelect implements ImportSelector {
    
    @Override
    public String[] selectImports(@NonNull AnnotationMetadata importingClassMetadata) {
        List<String> imports = new ArrayList<>();
        ClassLoader loader = getClass().getClassLoader();
        boolean present = ClassUtils.isPresent("org.springframework.data.redis.connection.RedisConnectionFactory",
                                               loader
        );
        if (present) {
            imports.add(RedisConfig.class.getName());
        }
        return imports.toArray(new String[0]);
    }
}
