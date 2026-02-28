package com.lrenyi.template.dataforge.audit;

import com.lrenyi.template.dataforge.audit.aspect.AuditLogAspect;
import com.lrenyi.template.dataforge.audit.enricher.AuditLogEnricher;
import com.lrenyi.template.dataforge.audit.model.AuditLogInfo;
import com.lrenyi.template.dataforge.audit.processor.AuditLogProcessor;
import com.lrenyi.template.dataforge.audit.resolver.AuditDescriptionResolver;
import com.lrenyi.template.dataforge.audit.service.AuditLogService;
import com.lrenyi.template.dataforge.controller.GenericEntityController;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 审计自动装配：Processor、Service、Aspect 及通用 Controller 的 resolver/enricher。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnExpression("'${app.template.enabled:true}' == 'true' && '${app.template.audit.enabled:false}' == 'true'")
@EnableAsync
public class DataforgeAuditAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean(AuditLogProcessor.class)
    public AuditLogProcessor defaultAuditLogProcessor() {
        return logInfo -> System.out.println("[Audit] " + logInfo);
    }
    
    @Bean
    @ConditionalOnBean(AuditLogProcessor.class)
    public AuditLogService auditLogService(AuditLogProcessor auditLogProcessor,
            @Value("${spring.application.name:unknown-service}") String serviceName,
            ObjectProvider<AuditDescriptionResolver> descriptionResolverProvider,
            ObjectProvider<AuditLogEnricher> enricherProvider) {
        return new AuditLogService(auditLogProcessor, serviceName, descriptionResolverProvider, enricherProvider);
    }
    
    @Bean
    @ConditionalOnBean(AuditLogService.class)
    public AuditLogAspect auditLogAspect(AuditLogService auditLogService) {
        return new AuditLogAspect(auditLogService);
    }
    
    @Bean
    @Order(0)
    public AuditDescriptionResolver genericEntityAuditDescriptionResolver() {
        return DataforgeAuditAutoConfiguration::resolve;
    }
    
    private static String resolve(ProceedingJoinPoint joinPoint, HttpServletRequest request) {
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
        return resolveMethodDescription(methodName, entity, args);
    }
    
    private static String resolveMethodDescription(String methodName, String entity, Object[] args) {
        return switch (methodName) {
            case "search" -> "搜索 " + entity;
            case "get" -> resolveGet(entity, args);
            case "create" -> "创建 " + entity;
            case "update" -> resolveUpdate(entity, args);
            case "delete" -> resolveDelete(entity, args);
            case "deleteBatch" -> "删除 " + entity;
            case "updateBatch" -> "更新 " + entity;
            case "export" -> "导出 Excel " + entity;
            case "executeAction" -> resolveExecuteAction(entity, args);
            default -> null;
        };
    }
    
    private static String resolveGet(String entity, Object[] args) {
        if (args.length > 1 && args[1] != null) {
            return "获取 " + entity + "/" + args[1];
        }
        return "获取 " + entity;
    }
    
    private static String resolveUpdate(String entity, Object[] args) {
        if (args.length > 1 && args[1] != null) {
            return "更新 " + entity + "/" + args[1];
        }
        return "更新 " + entity;
    }
    
    private static String resolveDelete(String entity, Object[] args) {
        if (args.length > 1 && args[1] != null) {
            return "删除 " + entity + "/" + args[1];
        }
        return "删除 " + entity;
    }
    
    private static String resolveExecuteAction(String entity, Object[] args) {
        if (args.length > 3 && args[2] != null && args[3] instanceof String actionName) {
            return "执行 " + entity + "/" + args[2] + "/" + actionName;
        }
        return "执行 " + entity;
    }
    
    @Bean
    @Order(0)
    public AuditLogEnricher genericEntityAuditLogEnricher() {
        return DataforgeAuditAutoConfiguration::enrich;
    }
    
    private static void enrich(ProceedingJoinPoint joinPoint, HttpServletRequest request, AuditLogInfo logInfo) {
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
}
