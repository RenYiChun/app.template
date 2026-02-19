package com.lrenyi.template.platform.domain;

import java.util.Date;
import com.lrenyi.template.platform.annotation.PlatformEntity;
import com.lrenyi.template.platform.annotation.Searchable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 操作日志 domain：按 platform 规则标注后自动拥有默认 Controller（列表/查询/导出等）。
 * 采集到的 {@link com.lrenyi.template.platform.audit.model.AuditLogInfo} 通过
 * EntityCrudService 写入本实体入库。
 */
@Setter
@Getter
@Entity
@Table(name = "sys_operation_log")
@PlatformEntity(
        pathSegment = "sys_operation_log", displayName = "操作日志", table = "sys_operation_log", generateDtos = false,
        enableDelete = false, enableDeleteBatch = false, enableUpdate = false, enableUpdateBatch = false
)
public class OperationLog extends BaseEntity<Long> {
    
    @Searchable(label = "用户名", order = 1)
    @Column(length = 128)
    private String userName;
    
    @Searchable(label = "操作描述", order = 5)
    @Column(length = 512)
    private String description;

    @Column(nullable = false)
    @Searchable(label = "操作时间", order = 6)
    private Date operationTime;

    private Long executionTimeMs;
    
    @Searchable(label = "请求IP", order = 2)
    @Column(length = 64)
    private String requestIp;
    
    @Searchable(label = "请求路径", order = 3)
    @Column(length = 512)
    private String requestUri;

    @Column(length = 16)
    private String requestMethod;
    
    @Searchable(label = "是否成功", order = 4)
    private boolean success;
    @Column(length = 2048)
    private String exceptionDetails;
    @Column(length = 128)
    private String serviceName;
    @Column(length = 64)
    private String serverIp;

    @Column(length = 256)
    private String reason;
    @Column(length = 64)
    private String targetType;
    @Column(length = 256)
    private String targetId;
    private Long affectedCount;
    @Column(length = 2048)
    private String extra;
}
