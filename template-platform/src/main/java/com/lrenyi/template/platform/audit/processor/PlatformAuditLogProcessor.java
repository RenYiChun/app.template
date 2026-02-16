package com.lrenyi.template.platform.audit.processor;

import com.lrenyi.template.platform.domain.OperationLog;
import com.lrenyi.template.platform.audit.model.AuditLogInfo;
import com.lrenyi.template.platform.meta.EntityMeta;
import com.lrenyi.template.platform.registry.EntityRegistry;
import com.lrenyi.template.platform.service.EntityCrudService;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 将采集到的操作日志通过 platform 的 EntityCrudService 写入 OperationLog 实体，实现入库。
 * OperationLog 作为 @PlatformEntity 由应用 scan-packages 扫描注册后，自动拥有 /api/operation_logs 等默认接口。
 */
public class PlatformAuditLogProcessor implements AuditLogProcessor {

    private static final String OPERATION_LOG_ENTITY_NAME = "OperationLog";

    private final EntityRegistry entityRegistry;
    private final EntityCrudService entityCrudService;

    public PlatformAuditLogProcessor(EntityRegistry entityRegistry, EntityCrudService entityCrudService) {
        this.entityRegistry = entityRegistry;
        this.entityCrudService = entityCrudService;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void process(AuditLogInfo auditLogInfo) {
        EntityMeta meta = entityRegistry.getByEntityName(OPERATION_LOG_ENTITY_NAME);
        if (meta == null) {
            return;
        }
        OperationLog entity = toEntity(auditLogInfo);
        entityCrudService.create(meta, entity);
    }

    private static OperationLog toEntity(AuditLogInfo info) {
        OperationLog e = new OperationLog();
        e.setUserName(info.getUserName());
        e.setDescription(info.getDescription());
        e.setOperationTime(info.getOperationTime() != null ? info.getOperationTime() : new java.util.Date());
        e.setExecutionTimeMs(info.getExecutionTimeMs());
        e.setRequestIp(info.getRequestIp());
        e.setRequestUri(info.getRequestUri());
        e.setRequestMethod(info.getRequestMethod());
        e.setSuccess(info.isSuccess());
        e.setExceptionDetails(info.getExceptionDetails());
        e.setServiceName(info.getServiceName());
        e.setServerIp(info.getServerIp());
        e.setReason(info.getReason());
        e.setTargetType(info.getTargetType());
        e.setTargetId(info.getTargetId());
        e.setAffectedCount(info.getAffectedCount());
        e.setExtra(info.getExtra());
        return e;
    }
}
