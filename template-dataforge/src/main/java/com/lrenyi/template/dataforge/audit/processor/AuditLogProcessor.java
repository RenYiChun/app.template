package com.lrenyi.template.dataforge.audit.processor;

import com.lrenyi.template.dataforge.audit.model.AuditLogInfo;

/**
 * 审计日志处理器接口。使用者需实现此接口来定义如何处理审计日志数据。
 */
@FunctionalInterface
public interface AuditLogProcessor {
    
    void process(AuditLogInfo auditLogInfo);
}
