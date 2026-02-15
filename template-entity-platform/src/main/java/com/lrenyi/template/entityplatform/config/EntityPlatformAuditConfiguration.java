package com.lrenyi.template.entityplatform.config;

import com.lrenyi.template.api.audit.resolver.AuditDescriptionResolver;
import com.lrenyi.template.entityplatform.controller.GenericEntityController;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 为通用 Controller 注册操作日志描述解析器（由 Bootstrap 在存在 template-api 时加载），
 * 使审计日志显示「列表 users」「获取 users/1」等可读描述。
 */
@Configuration(proxyBeanMethods = false)
public class EntityPlatformAuditConfiguration {

    @Bean
    @Order(0)
    public AuditDescriptionResolver genericEntityAuditDescriptionResolver() {
        return this::resolve;
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
        switch (methodName) {
            case "list":
                return "列表 " + entity;
            case "get":
                if (args.length > 1 && args[1] != null) {
                    return "获取 " + entity + "/" + args[1];
                }
                return "获取 " + entity;
            case "create":
                return "创建 " + entity;
            case "update":
                if (args.length > 1 && args[1] != null) {
                    return "更新 " + entity + "/" + args[1];
                }
                return "更新 " + entity;
            case "delete":
                if (args.length > 1 && args[1] != null) {
                    return "删除 " + entity + "/" + args[1];
                }
                return "删除 " + entity;
            case "deleteBatch":
                return "批量删除 " + entity;
            case "updateBatch":
                return "批量更新 " + entity;
            case "export":
                return "导出 Excel " + entity;
            case "executeAction":
                if (args.length > 3 && args[2] != null && args[3] instanceof String actionName) {
                    return "执行 " + entity + "/" + args[2] + "/" + actionName;
                }
                return "执行 " + entity;
            default:
                return null;
        }
    }
}
