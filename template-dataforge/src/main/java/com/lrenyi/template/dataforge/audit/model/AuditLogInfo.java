package com.lrenyi.template.dataforge.audit.model;

import java.io.Serializable;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

/**
 * 操作/审计日志信息，记录维度对齐 5W2H。
 * <ul>
 *   <li>What: description, requestUri, requestMethod, targetType, targetId</li>
 *   <li>Why: reason</li>
 *   <li>When: operationTime, executionTimeMs</li>
 *   <li>Where: requestIp, serverIp, serviceName</li>
 *   <li>Who: userName</li>
 *   <li>How: requestMethod, success, exceptionDetails</li>
 *   <li>How much: executionTimeMs, affectedCount, extra</li>
 * </ul>
 */
@Setter
@Getter
public class AuditLogInfo implements Serializable {

    /** 操作人（Who） */
    private String userName;
    /** 操作描述（What） */
    private String description;
    /** 操作时间（When） */
    private Date operationTime;
    /** 执行耗时毫秒（When/How much） */
    private Long executionTimeMs;
    /** 请求 IP（Where） */
    private String requestIp;
    /** 请求 URI（What） */
    private String requestUri;
    /** 请求方法（What/How） */
    private String requestMethod;
    /** 是否成功（How） */
    private boolean success;
    /** 异常信息（How，失败时） */
    private String exceptionDetails;
    /** 服务名（Where） */
    private String serviceName;
    /** 服务器 IP（Where） */
    private String serverIp;

    /** 操作原因/业务意图（Why），如工单号、审批单号、备注 */
    private String reason;
    /** 操作对象类型（What 结构化），如 User、Order */
    private String targetType;
    /** 操作对象 ID（What 结构化），多个用逗号分隔 */
    private String targetId;
    /** 影响数量（How much 业务量），如删除条数、导出行数 */
    private Long affectedCount;
    /** 扩展信息 JSON（How much 等），便于后续加金额等不改表结构 */
    private String extra;

    @Override
    public String toString() {
        return "AuditLogInfo{"
                + "userName='" + userName + '\''
                + ", description='" + description + '\''
                + ", operationTime=" + operationTime
                + ", executionTimeMs=" + executionTimeMs
                + ", requestIp='" + requestIp + '\''
                + ", requestUri='" + requestUri + '\''
                + ", requestMethod='" + requestMethod + '\''
                + ", success=" + success
                + ", exceptionDetails='" + exceptionDetails + '\''
                + ", serviceName='" + serviceName + '\''
                + ", serverIp='" + serverIp + '\''
                + ", reason='" + reason + '\''
                + ", targetType='" + targetType + '\''
                + ", targetId='" + targetId + '\''
                + ", affectedCount=" + affectedCount
                + ", extra='" + extra + '\''
                + '}';
    }
}
