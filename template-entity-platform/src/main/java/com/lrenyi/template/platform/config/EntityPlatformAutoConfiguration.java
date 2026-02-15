package com.lrenyi.template.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.platform.action.EntityActionExecutor;
import com.lrenyi.template.platform.controller.GenericEntityController;
import com.lrenyi.template.platform.controller.OpenApiController;
import com.lrenyi.template.platform.registry.ActionRegistry;
import com.lrenyi.template.platform.support.EntityPlatformAspect;
import com.lrenyi.template.platform.support.EntityPlatformExceptionHandler;
import com.lrenyi.template.platform.registry.EntityRegistry;
import com.lrenyi.template.platform.service.EntityCrudService;
import com.lrenyi.template.platform.service.InMemoryEntityCrudService;
import com.lrenyi.template.platform.support.MetaScanner;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(EntityPlatformProperties.class)
@ConditionalOnProperty(name = "app.platform.enabled", havingValue = "true", matchIfMissing = true)
public class EntityPlatformAutoConfiguration {

    @Bean
    public EntityRegistry entityRegistry() {
        return new EntityRegistry();
    }

    @Bean
    public ActionRegistry actionRegistry() {
        return new ActionRegistry();
    }

    @Bean
    public MetaScanner metaScanner(
            EntityRegistry entityRegistry,
            ActionRegistry actionRegistry,
            EntityPlatformProperties properties) {
        String base = properties.getScanPackages();
        return new MetaScanner(entityRegistry, actionRegistry, base);
    }

    @Bean("defaultEntityCrudService")
    @ConditionalOnMissingBean(name = "defaultEntityCrudService")
    public EntityCrudService defaultEntityCrudService() {
        return new InMemoryEntityCrudService();
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(EntityCrudService.class)
    public EntityCrudService entityCrudService(
            @Qualifier("defaultEntityCrudService") EntityCrudService defaultService) {
        return defaultService;
    }

    @Bean
    public EntityPlatformAspect entityPlatformAspect() {
        return new EntityPlatformAspect();
    }

    @Bean
    public EntityPlatformExceptionHandler entityPlatformExceptionHandler() {
        return new EntityPlatformExceptionHandler();
    }

    @Bean
    public OpenApiController openApiController(EntityRegistry entityRegistry,
                                               org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping handlerMapping) {
        return new OpenApiController(entityRegistry, handlerMapping);
    }

    @Bean
    public GenericEntityController genericEntityController(
            EntityRegistry entityRegistry,
            ActionRegistry actionRegistry,
            EntityCrudService entityCrudService,
            EntityPlatformProperties properties,
            ObjectMapper objectMapper) {
        return new GenericEntityController(
                entityRegistry, actionRegistry, entityCrudService, properties, objectMapper);
    }

    @Bean
    public EntityPlatformInitializer entityPlatformInitializer(
            MetaScanner metaScanner,
            EntityPlatformProperties properties,
            ApplicationContext applicationContext) {
        return new EntityPlatformInitializer(metaScanner, properties, applicationContext);
    }

    /**
     * 启动时执行扫描注册。
     */
    public static class EntityPlatformInitializer {

        private final MetaScanner metaScanner;
        private final EntityPlatformProperties properties;
        private final ApplicationContext applicationContext;

        public EntityPlatformInitializer(
                MetaScanner metaScanner,
                EntityPlatformProperties properties,
                ApplicationContext applicationContext) {
            this.metaScanner = metaScanner;
            this.properties = properties;
            this.applicationContext = applicationContext;
        }

        @PostConstruct
        public void init() {
            List<Object> executors = new ArrayList<>(
                    applicationContext.getBeansOfType(EntityActionExecutor.class).values());
            metaScanner.scanAndRegister(executors);
        }
    }
}
