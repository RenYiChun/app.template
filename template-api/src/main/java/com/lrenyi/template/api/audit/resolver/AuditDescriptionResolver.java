package com.lrenyi.template.api.audit.resolver;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * 操作日志描述解析器：可为通用 Controller 等生成可读的操作描述（如「列表 users」「获取 users/1」）。
 * 若存在多个实现，可配合 @Order 或 @Primary 指定唯一生效的 Bean。
 */
@FunctionalInterface
public interface AuditDescriptionResolver {

    /**
     * 根据当前请求与方法调用生成操作描述；若无法解析则返回 null，由审计模块回退到默认描述。
     *
     * @param joinPoint 当前切点
     * @param request   当前请求
     * @return 操作描述，或 null 表示使用默认逻辑
     */
    String resolve(ProceedingJoinPoint joinPoint, HttpServletRequest request);
}
