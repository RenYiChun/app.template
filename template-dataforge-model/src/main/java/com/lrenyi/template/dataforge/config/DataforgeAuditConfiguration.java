package com.lrenyi.template.dataforge.config;

import com.lrenyi.template.dataforge.audit.aspect.AuditLogAspect;
import com.lrenyi.template.dataforge.audit.enricher.AuditLogEnricher;
import com.lrenyi.template.dataforge.audit.model.AuditLogInfo;
import com.lrenyi.template.dataforge.audit.processor.AuditLogProcessor;
import com.lrenyi.template.dataforge.audit.processor.DataforgeAuditLogProcessor;
import com.lrenyi.template.dataforge.audit.resolver.AuditDescriptionResolver;
import com.lrenyi.template.dataforge.audit.service.AuditLogService;
import com.lrenyi.template.dataforge.controller.GenericEntityController;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import com.lrenyi.template.dataforge.service.EntityCrudService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 审计机制：注册 Processor/Service/Aspect 及通用 Controller 的描述解析器与增强器。
 * 使审计日志显示「列表 users」「获取 users/1」等可读描述，并补充 targetType、targetId 等 5W2H 维度。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DataforgeAuditConfiguration {

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    @ConditionalOnProperty(name = "app.template.audit.enabled", havingValue = "true")
    static class AuditLogBeans {

        @Bean
        @ConditionalOnMissingClass("jakarta.persistence.EntityManager")
        public AuditLogProcessor auditLogProcessor() {
            return System.out::println;
        }

        @Bean
        @ConditionalOnClass(name = "jakarta.persistence.EntityManager")
        public AuditLogProcessor dataforgeAuditLogProcessor(EntityRegistry entityRegistry,
                                                           EntityCrudService entityCrudService) {
            return new DataforgeAuditLogProcessor(entityRegistry, entityCrudService);
        }

        @Bean
        public AuditLogService auditLogService(AuditLogProcessor auditLogProcessor,
                                               @Value("${spring.application.name:unknown-service}") String serviceName,
                                               ObjectProvider<AuditDescriptionResolver> descriptionResolverProvider,
                                               ObjectProvider<AuditLogEnricher> enricherProvider) {
            return new AuditLogService(auditLogProcessor, serviceName, descriptionResolverProvider, enricherProvider);
        }

        @Bean
        public AuditLogAspect auditLogAspect(AuditLogService auditLogService) {
            return new AuditLogAspect(auditLogService);
        }
    }

    @Bean
    @Order(0)
    public AuditDescriptionResolver genericEntityAuditDescriptionResolver() {
        return this::resolve;
    }

    @Bean
    @Order(0)
    public AuditLogEnricher genericEntityAuditLogEnricher() {
        return this::enrich;
    }

    private void enrich(ProceedingJoinPoint joinPoint, HttpServletRequest request, AuditLogInfo logInfo) {
        Object target = joinPoint.getTarget();
        if (!(target instanceof GenericEntityController)) {
            return;
        }
        if (!(joinPoint.getSignature() instanceof MethodSignature signature)) {
            return;
        }
        String methodName = signature.getMethod().getName();
        Object[] args = joinPoint.getArgs();
        String entity = args.length > 0 && args[0] instanceof String ? (String) args[0] : null;
        if (entity == null || entity.isEmpty()) {
            return;
        }
        logInfo.setTargetType(entity);
        switch (methodName) {
            case "get":
            case "update":
            case "delete":
                if (args.length > 1 && args[1] != null) {
                    logInfo.setTargetId(String.valueOf(args[1]));
                }
                break;
            case "executeAction":
                if (args.length > 2 && args[2] != null) {
                    logInfo.setTargetId(String.valueOf(args[2]));
                }
                break;
            default:
                break;
        }
    }

    private String resolve(ProceedingJoinPoint joinPoint, HttpServletRequest request) {
        Object target = joinPoint.getTarget();
        if (!(target instanceof GenericEntityController)) {
            return null;
        }
        if (!(joinPoint.getSignature() instanceof MethodSignature signature)) {
            return null;
        }
        String methodName = signature.getMethod().getName();
        Object[] args = joinPoint.getArgs();
        String entity = args.length > 0 && args[0] instanceof String ? (String) args[0] : null;
        if (entity == null || entity.isEmpty()) {
            return null;
        }
        return switch (methodName) {
            case "search" -> "搜索 " + entity;
            case "get" -> {
                if (args.length > 1 && args[1] != null) {
                    yield "获取 " + entity + "/" + args[1];
                }
                yield "获取 " + entity;
            }
            case "create" -> "创建 " + entity;
            case "update" -> {
                if (args.length > 1 && args[1] != null) {
                    yield "更新 " + entity + "/" + args[1];
                }
                yield "更新 " + entity;
            }
            case "delete" -> {
                if (args.length > 1 && args[1] != null) {
                    yield "删除 " + entity + "/" + args[1];
                }
                yield "删除 " + entity;
            }
            case "deleteBatch" -> "批量删除 " + entity;
            case "updateBatch" -> "批量更新 " + entity;
            case "export" -> "导出 Excel " + entity;
            case "executeAction" -> {
                if (args.length > 3 && args[2] != null && args[3] instanceof String actionName) {
                    yield "执行 " + entity + "/" + args[2] + "/" + actionName;
                }
                yield "执行 " + entity;
            }
            default -> null;
        };
    }
}
