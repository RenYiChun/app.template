package com.lrenyi.template.api.audit.model;

import java.io.Serializable;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AuditLogInfo implements Serializable {
    private String userName;
    private String description;
    private Date operationTime;
    private Long executionTimeMs;
    private String requestIp;
    private String requestUri;
    private String requestMethod;
    private boolean success;
    private String exceptionDetails;
    private String serviceName;
    private String serverIp;
    
    @Override
    public String toString() {
        return "AuditLogInfo{" + "userName='" + userName + '\'' + ", description='" + description + '\'' + ", " +
                "operationTime=" + operationTime + ", executionTimeMs=" + executionTimeMs + ", requestIp='" + requestIp + '\'' + ", requestUri='" + requestUri + '\'' + ", requestMethod='" + requestMethod + '\'' + ", success=" + success + ", exceptionDetails='" + exceptionDetails + '\'' + ", serviceName='" + serviceName + '\'' + ", serverIp='" + serverIp + '\'' + '}';
    }
}