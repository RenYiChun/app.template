package com.lrenyi.template.platform.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.platform.action.EntityActionExecutor;
import com.lrenyi.template.platform.controller.DocsUiController;
import com.lrenyi.template.platform.controller.GenericEntityController;
import com.lrenyi.template.platform.controller.OpenApiController;
import com.lrenyi.template.platform.permission.DefaultPlatformPermissionChecker;
import com.lrenyi.template.platform.permission.PlatformPermissionChecker;
import com.lrenyi.template.platform.registry.ActionRegistry;
import com.lrenyi.template.platform.registry.EntityRegistry;
import com.lrenyi.template.platform.service.EntityCrudService;
import com.lrenyi.template.platform.service.EntityCrudServiceRouter;
import com.lrenyi.template.platform.service.InMemoryEntityCrudService;
import com.lrenyi.template.platform.service.PathSegmentAwareCrudService;
import com.lrenyi.template.platform.support.EntityPlatformAspect;
import com.lrenyi.template.platform.support.EntityPlatformExceptionHandler;
import com.lrenyi.template.platform.support.MetaScanner;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;

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
            @Qualifier("defaultEntityCrudService") EntityCrudService defaultService,
            List<PathSegmentAwareCrudService> pathSegmentAwareServices) {
        Map<String, EntityCrudService> pathSegmentToDelegate = new LinkedHashMap<>();
        if (pathSegmentAwareServices != null) {
            for (PathSegmentAwareCrudService service : pathSegmentAwareServices) {
                String segment = service.getPathSegment();
                if (segment != null && !segment.isBlank() && !pathSegmentToDelegate.containsKey(segment)) {
                    pathSegmentToDelegate.put(segment.trim(), service);
                }
            }
        }
        return new EntityCrudServiceRouter(defaultService, pathSegmentToDelegate);
    }

    @Bean
    public EntityPlatformAspect entityPlatformAspect() {
        return new EntityPlatformAspect();
    }

    @Bean
    public EntityPlatformExceptionHandler entityPlatformExceptionHandler(EntityPlatformProperties properties) {
        return new EntityPlatformExceptionHandler(properties);
    }

    @Bean
    @ConditionalOnMissingBean(PlatformPermissionChecker.class)
    public PlatformPermissionChecker platformPermissionChecker() {
        return new DefaultPlatformPermissionChecker();
    }

    @Bean
    public OpenApiController openApiController(EntityRegistry entityRegistry,
            org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping handlerMapping) {
        return new OpenApiController(entityRegistry, handlerMapping);
    }

    @Bean
    @ConditionalOnProperty(name = "app.platform.docs-ui-enabled", havingValue = "true", matchIfMissing = true)
    public DocsUiController docsUiController(EntityPlatformProperties properties) {
        return new DocsUiController(properties);
    }

    @Bean
    public GenericEntityController genericEntityController(
            EntityRegistry entityRegistry,
            ActionRegistry actionRegistry,
            EntityCrudService entityCrudService,
            EntityPlatformProperties properties,
            PlatformPermissionChecker platformPermissionChecker,
            ObjectMapper objectMapper,
            org.springframework.beans.factory.ObjectProvider<jakarta.validation.Validator> validatorProvider) {
        return new GenericEntityController(
                entityRegistry, actionRegistry, entityCrudService, properties,
                platformPermissionChecker, objectMapper, validatorProvider);
    }

    @Bean
    public EntityPlatformInitializer entityPlatformInitializer(
            MetaScanner metaScanner,
            EntityRegistry entityRegistry,
            ApplicationContext applicationContext,
            ObjectProvider<EntityManagerFactory> entityManagerFactoryProvider) {
        return new EntityPlatformInitializer(metaScanner,
                                             entityRegistry,
                                             applicationContext,
                                             entityManagerFactoryProvider
        );
    }

    /**
     * 在 ApplicationRunner 阶段执行实体扫描与 Action 注册（启动完成后执行，避免在 refresh 期间阻塞）。
     */
    public static class EntityPlatformInitializer implements ApplicationRunner, Ordered {
        
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EntityPlatformInitializer.class);

        private final MetaScanner metaScanner;
        private final EntityRegistry entityRegistry;
        private final ApplicationContext applicationContext;
        private final ObjectProvider<EntityManagerFactory> entityManagerFactoryProvider;

        public EntityPlatformInitializer(
                MetaScanner metaScanner,
                EntityRegistry entityRegistry,
                ApplicationContext applicationContext,
                ObjectProvider<EntityManagerFactory> entityManagerFactoryProvider) {
            this.metaScanner = metaScanner;
            this.entityRegistry = entityRegistry;
            this.applicationContext = applicationContext;
            this.entityManagerFactoryProvider = entityManagerFactoryProvider;
        }
        
        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
        
        @Override
        public void run(ApplicationArguments args) {
            try {
                EntityManagerFactory emf = entityManagerFactoryProvider.getIfAvailable();
                metaScanner.setEntityManagerFactory(emf);
                List<Object> executors =
                        new ArrayList<>(applicationContext.getBeansOfType(EntityActionExecutor.class).values());
                if (entityRegistry.getAll().isEmpty()) {
                    log.info("平台实体扫描开始（ApplicationRunner）");
                    metaScanner.scanAndRegister(executors);
                    log.info("平台实体扫描完成，已注册实体数: {}", entityRegistry.getAll().size());
                } else {
                    metaScanner.registerActionExecutors(executors);
                    log.debug("Action 执行器已注册");
                }
            } catch (Exception e) {
                log.error("平台实体扫描失败", e);
                throw e;
            }
        }
    }
}
