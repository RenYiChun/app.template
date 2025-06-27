package com.lrenyi.template.api.audit.processor;

import com.lrenyi.template.api.audit.model.AuditLogInfo;

/**
 * 审计日志处理器接口
 * 使用者需要实现此接口来定义如何处理审计日志数据
 */
public interface AuditLogProcessor {
    void process(AuditLogInfo auditLogInfo);
}