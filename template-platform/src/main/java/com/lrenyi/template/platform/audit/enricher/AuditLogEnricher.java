package com.lrenyi.template.platform.audit.enricher;

import com.lrenyi.template.platform.audit.model.AuditLogInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * 操作日志增强器：在写入前根据请求与切点补充 reason、targetType、targetId、affectedCount、extra 等。
 * 可存在多个实现，按 @Order 顺序依次调用；仅对能识别的请求补充，不认识的直接返回。
 */
public interface AuditLogEnricher {

    /**
     * 根据当前请求与方法调用补充日志信息；不修改表示不处理。
     *
     * @param joinPoint 当前切点
     * @param request   当前请求
     * @param logInfo   已填充基本字段的日志信息，可在此对象上设置 reason、targetType、targetId、affectedCount、extra
     */
    void enrich(ProceedingJoinPoint joinPoint, HttpServletRequest request, AuditLogInfo logInfo);
}
