package com.lrenyi.template.dataforge.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.dataforge.DataforgeAutoConfiguration;
import com.lrenyi.template.dataforge.jpa.service.JpaEntityCrudService;
import com.lrenyi.template.dataforge.service.EntityCrudService;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * 当 classpath 存在 JPA 时注册基于 EntityManager 的通用 CRUD 服务，命名为 defaultEntityCrudService。
 * 必须在 DataforgeAutoConfiguration 之前执行，避免与内存实现同名冲突。
 */
@AutoConfiguration
@AutoConfigureBefore(DataforgeAutoConfiguration.class)
@ConditionalOnClass(EntityManager.class)
public class JpaEntityCrudServiceAutoConfiguration {
    
    @Bean("defaultEntityCrudService")
    public EntityCrudService defaultEntityCrudService(EntityManager entityManager, ObjectMapper objectMapper) {
        return new JpaEntityCrudService(entityManager, objectMapper);
    }
}
