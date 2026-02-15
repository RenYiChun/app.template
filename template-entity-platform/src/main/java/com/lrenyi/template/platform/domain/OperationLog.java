package com.lrenyi.template.platform.domain;

import com.lrenyi.template.platform.annotation.PlatformEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

/**
 * 操作日志 domain：按 platform 规则标注后自动拥有默认 Controller（列表/查询/导出等）。
 * 采集到的 {@link com.lrenyi.template.platform.audit.model.AuditLogInfo} 通过 EntityCrudService 写入本实体入库。
 */
@Setter
@Getter
@Entity
@Table(name = "operation_log")
@PlatformEntity(
        pathSegment = "operation_logs",
        displayName = "操作日志",
        table = "operation_log",
        generateDtos = false
)
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 128)
    private String userName;
    @Column(length = 512)
    private String description;
    @Column(nullable = false)
    private Date operationTime;
    private Long executionTimeMs;
    @Column(length = 64)
    private String requestIp;
    @Column(length = 512)
    private String requestUri;
    @Column(length = 16)
    private String requestMethod;
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
