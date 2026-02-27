package com.lrenyi.template.dataforge.backend.domain;

import java.util.Date;
import com.lrenyi.template.dataforge.annotation.DataforgeEntity;
import com.lrenyi.template.dataforge.annotation.DataforgeField;
import com.lrenyi.template.dataforge.jpa.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 操作日志 domain：按 dataforge 规则标注后自动拥有默认 Controller（列表/查询/导出等）。
 * 采集到的 {@link com.lrenyi.template.dataforge.audit.model.AuditLogInfo} 通过
 * EntityCrudService 写入本实体入库。
 */
@Setter
@Getter
@Entity
@Table(name = "sys_operation_log")
@DataforgeEntity(
        pathSegment = "operation_log",
        displayName = "操作日志",
        table = "sys_operation_log",
        generateDtos = false,
        enableDelete = false,
        enableDeleteBatch = false,
        enableUpdate = false,
        enableUpdateBatch = false
)
public class OperationLog extends BaseEntity<Long> {

    @DataforgeField(label = "用户名", order = 1, searchable = true)
    @Column(length = 128)
    private String userName;

    @DataforgeField(label = "操作描述", order = 5, searchable = true)
    @Column(length = 512)
    private String description;

    @Column(nullable = false)
    @DataforgeField(label = "操作时间", order = 6, searchable = true)
    private Date operationTime;

    private Long executionTimeMs;

    @DataforgeField(label = "请求IP", order = 2, searchable = true)
    @Column(length = 64)
    private String requestIp;

    @DataforgeField(label = "请求路径", order = 3, searchable = true)
    @Column(length = 512)
    private String requestUri;

    @Column(length = 16)
    private String requestMethod;

    @DataforgeField(label = "是否成功", order = 4, searchable = true)
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
