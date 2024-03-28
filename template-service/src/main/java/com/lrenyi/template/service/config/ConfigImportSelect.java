package com.lrenyi.template.service.config;

import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

public class ConfigImportSelect implements ImportSelector {
    
    @Override
    public String[] selectImports(@NonNull AnnotationMetadata importingClassMetadata) {
        List<String> configs = new ArrayList<>();
        configs.add(DefaultFeignConfig.class.getName());
        return configs.toArray(new String[0]);
    }
}
