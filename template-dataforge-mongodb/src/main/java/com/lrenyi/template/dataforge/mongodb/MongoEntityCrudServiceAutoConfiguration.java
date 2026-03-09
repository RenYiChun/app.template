package com.lrenyi.template.dataforge.mongodb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.dataforge.mongodb.service.MongoEntityCrudService;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * 当 classpath 存在 MongoTemplate 时注册 MongoEntityCrudService，
 * 作为 StorageTypeAwareCrudService 处理 storage=MONGO 的实体。
 * 注入 EntityRegistry 时 list 支持 $lookup 填充 _display。
 */
@AutoConfiguration
@ConditionalOnClass(MongoTemplate.class)
public class MongoEntityCrudServiceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MongoEntityCrudService.class)
    public MongoEntityCrudService mongoEntityCrudService(MongoTemplate mongoTemplate,
            ObjectMapper objectMapper,
            ObjectProvider<EntityRegistry> entityRegistryProvider) {
        EntityRegistry registry = entityRegistryProvider.getIfAvailable();
        return new MongoEntityCrudService(mongoTemplate, objectMapper, registry);
    }
}
