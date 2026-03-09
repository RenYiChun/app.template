package com.lrenyi.template.dataforge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.dataforge.action.EntityActionExecutor;
import com.lrenyi.template.dataforge.config.DataforgeProperties;
import com.lrenyi.template.dataforge.controller.DocsUiController;
import com.lrenyi.template.dataforge.controller.GenericEntityController;
import com.lrenyi.template.dataforge.controller.MetadataController;
import com.lrenyi.template.dataforge.controller.OpenApiController;
import com.lrenyi.template.dataforge.mapper.BaseMapper;
import com.lrenyi.template.dataforge.permission.DataPermissionApplicator;
import com.lrenyi.template.dataforge.permission.DataforgePermissionChecker;
import com.lrenyi.template.dataforge.permission.DefaultDataforgePermissionChecker;
import com.lrenyi.template.dataforge.registry.ActionRegistry;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import com.lrenyi.template.dataforge.service.CascadeDeleteService;
import com.lrenyi.template.dataforge.service.EntityCrudService;
import com.lrenyi.template.dataforge.service.EntityCrudServiceRouter;
import com.lrenyi.template.dataforge.service.InMemoryEntityCrudService;
import com.lrenyi.template.dataforge.service.PathSegmentAwareCrudService;
import com.lrenyi.template.dataforge.service.StorageTypeAwareCrudService;
import com.lrenyi.template.dataforge.validation.AssociationValidator;
import com.lrenyi.template.dataforge.support.DataforgeAspect;
import com.lrenyi.template.dataforge.support.DataforgeExceptionHandler;
import com.lrenyi.template.dataforge.support.DataforgeServices;
import com.lrenyi.template.dataforge.support.EntityMapperProvider;
import com.lrenyi.template.dataforge.support.MetaScanner;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import jakarta.validation.Validator;
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
import org.springframework.core.convert.ConversionService;

@AutoConfiguration
@EnableConfigurationProperties(DataforgeProperties.class)
@ConditionalOnProperty(name = "app.dataforge.enabled", havingValue = "true", matchIfMissing = true)
public class DataforgeAutoConfiguration {
    
    @Bean
    public EntityRegistry entityRegistry() {
        return new EntityRegistry();
    }
    
    @Bean
    public ActionRegistry actionRegistry() {
        return new ActionRegistry();
    }
    
    @Bean
    public MetaScanner metaScanner(EntityRegistry entityRegistry,
            ActionRegistry actionRegistry,
            DataforgeProperties properties) {
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
    public EntityCrudService entityCrudService(@Qualifier("defaultEntityCrudService") EntityCrudService defaultService,
            List<PathSegmentAwareCrudService> pathSegmentAwareServices,
            ObjectProvider<List<StorageTypeAwareCrudService>> storageTypeAwareServicesProvider) {
        Map<String, EntityCrudService> pathSegmentToDelegate = new LinkedHashMap<>();
        if (pathSegmentAwareServices != null) {
            for (PathSegmentAwareCrudService service : pathSegmentAwareServices) {
                String segment = service.getPathSegment();
                if (segment != null && !segment.isBlank() && !pathSegmentToDelegate.containsKey(segment)) {
                    pathSegmentToDelegate.put(segment.trim(), service);
                }
            }
        }
        Map<String, EntityCrudService> storageTypeToDelegate = new LinkedHashMap<>();
        List<StorageTypeAwareCrudService> storageTypeAwareServices =
                storageTypeAwareServicesProvider.getIfAvailable(List::of);
        for (StorageTypeAwareCrudService service : storageTypeAwareServices) {
            String st = service.getStorageType();
            if (st != null && !st.isBlank() && !storageTypeToDelegate.containsKey(st)) {
                storageTypeToDelegate.put(st, service);
            }
        }
        return new EntityCrudServiceRouter(defaultService, pathSegmentToDelegate, storageTypeToDelegate);
    }
    
    @Bean
    public DataforgeAspect dataforgeAspect(MeterRegistry meterRegistry) {
        return new DataforgeAspect(meterRegistry);
    }
    
    @Bean
    public DataforgeExceptionHandler dataforgeExceptionHandler(DataforgeProperties properties) {
        return new DataforgeExceptionHandler(properties);
    }
    
    @Bean
    @ConditionalOnMissingBean(DataforgePermissionChecker.class)
    public DataforgePermissionChecker dataforgePermissionChecker() {
        return new DefaultDataforgePermissionChecker();
    }
    
    @Bean
    public OpenApiController openApiController(EntityRegistry entityRegistry,
            @Qualifier("requestMappingHandlerMapping")
            org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping handlerMapping,
            EntityMapperProvider mapperProvider) {
        return new OpenApiController(entityRegistry, handlerMapping, mapperProvider);
    }
    
    @Bean
    public MetadataController metadataController(EntityRegistry entityRegistry) {
        return new MetadataController(entityRegistry);
    }
    
    @Bean
    @ConditionalOnProperty(name = "app.dataforge.docs-ui-enabled", havingValue = "true", matchIfMissing = true)
    public DocsUiController docsUiController(DataforgeProperties properties) {
        return new DocsUiController(properties);
    }
    
    @Bean
    public EntityMapperProvider entityMapperProvider(List<BaseMapper<?, ?, ?, ?, ?>> mappers) {
        return new EntityMapperProvider(mappers);
    }
    
    @Bean
    @ConditionalOnMissingBean(AssociationValidator.class)
    public AssociationValidator associationValidator(EntityRegistry entityRegistry,
            EntityCrudService entityCrudService) {
        return new AssociationValidator(entityRegistry, entityCrudService);
    }

    @Bean
    @ConditionalOnMissingBean(CascadeDeleteService.class)
    public CascadeDeleteService cascadeDeleteService(EntityRegistry entityRegistry,
            EntityCrudService entityCrudService) {
        return new CascadeDeleteService(entityRegistry, entityCrudService);
    }

    @Bean
    public DataforgeServices dataforgeServices(EntityRegistry entityRegistry,
            ActionRegistry actionRegistry,
            EntityCrudService entityCrudService,
            DataforgeProperties properties,
            DataforgePermissionChecker dataforgePermissionChecker,
            ObjectMapper objectMapper,
            ObjectProvider<Validator> validatorProvider,
            ConversionService conversionService,
            EntityMapperProvider entityMapperProvider,
            ObjectProvider<AssociationValidator> associationValidatorProvider,
            ObjectProvider<CascadeDeleteService> cascadeDeleteServiceProvider,
            ObjectProvider<DataPermissionApplicator> dataPermissionApplicatorProvider,
            ObjectProvider<com.lrenyi.template.dataforge.support.EntityChangeNotifier> entityChangeNotifierProvider,
            ObjectProvider<com.lrenyi.template.dataforge.support.AssociationChangeAuditor> associationChangeAuditorProvider) {
        return new DataforgeServices(entityRegistry,
                                     actionRegistry,
                                     entityCrudService,
                                     properties,
                                     dataforgePermissionChecker,
                                     objectMapper,
                                     validatorProvider,
                                     conversionService,
                                     entityMapperProvider,
                                     associationValidatorProvider,
                                     cascadeDeleteServiceProvider,
                                     dataPermissionApplicatorProvider,
                                     entityChangeNotifierProvider,
                                     associationChangeAuditorProvider
        );
    }
    
    @Bean
    public GenericEntityController genericEntityController(DataforgeServices dataforgeServices) {
        return new GenericEntityController(dataforgeServices);
    }
    
    @Bean
    public DataforgeInitializer dataforgeInitializer(MetaScanner metaScanner,
            EntityRegistry entityRegistry,
            ApplicationContext applicationContext,
            ObjectProvider<EntityManagerFactory> entityManagerFactoryProvider) {
        return new DataforgeInitializer(metaScanner, entityRegistry, applicationContext, entityManagerFactoryProvider);
    }
    
    /**
     * 在 ApplicationRunner 阶段执行实体扫描与 Action 注册（启动完成后执行，避免在 refresh 期间阻塞）。
     */
    @lombok.extern.slf4j.Slf4j
    public static class DataforgeInitializer implements ApplicationRunner, Ordered {
        
        private final MetaScanner metaScanner;
        private final EntityRegistry entityRegistry;
        private final ApplicationContext applicationContext;
        private final ObjectProvider<EntityManagerFactory> entityManagerFactoryProvider;
        
        public DataforgeInitializer(MetaScanner metaScanner,
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
