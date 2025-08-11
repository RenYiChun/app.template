package com.lrenyi.template.api.audit.service;

import com.lrenyi.template.api.audit.annotation.AuditLog;
import com.lrenyi.template.api.audit.model.AuditLogInfo;
import com.lrenyi.template.api.audit.processor.AuditLogProcessor;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;

@Slf4j
public class AuditLogService {
    
    private final AuditLogProcessor auditLogProcessor;
    private final String serviceName;
    
    private final String serverIp;
    
    public AuditLogService(AuditLogProcessor auditLogProcessor, String serviceName) {
        this.auditLogProcessor = auditLogProcessor;
        this.serviceName = serviceName;
        this.serverIp = getServerIp();
    }
    
    private String getServerIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.warn("failed to get server IP address", e);
            return "unknown";
        }
    }
    
    @Async
    public void saveLog(ProceedingJoinPoint joinPoint,
                        HttpServletRequest request,
                        SecurityContext context,
                        long time,
                        Throwable e) {
        AuditLogInfo logInfo = new AuditLogInfo();
        logInfo.setExecutionTimeMs(time);
        logInfo.setOperationTime(new Date());
        
        // 设置操作状态
        logInfo.setSuccess(e == null);
        if (e != null) {
            logInfo.setExceptionDetails(e.getMessage());
        }
        
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        AuditLog auditLogAnnotation = method.getAnnotation(AuditLog.class);
        if (auditLogAnnotation != null) {
            logInfo.setDescription(auditLogAnnotation.description());
        } else {
            // Provide a default description if the annotation is not present
            String className = joinPoint.getTarget().getClass().getSimpleName();
            String methodName = signature.getName();
            logInfo.setDescription(className + "#" + methodName);
        }
        if (request != null) {
            logInfo.setRequestIp(getIpAddress(request));
            logInfo.setRequestUri(request.getRequestURI());
            logInfo.setRequestMethod(request.getMethod());
            Authentication authentication = context.getAuthentication();
            String name = authentication.getName();
            if (authentication instanceof BearerTokenAuthentication bearerAuth) {
                name = String.valueOf(bearerAuth.getTokenAttributes().get(OAuth2TokenIntrospectionClaimNames.USERNAME));
            } else if (authentication instanceof JwtAuthenticationToken jwtToken) {
                name = jwtToken.getToken().getClaimAsString(OAuth2TokenIntrospectionClaimNames.USERNAME);
            } else if (!StringUtils.hasLength(name)) {
                log.warn("not find user info, the type of Authentication is: {}", authentication.getClass().getName());
            }
            logInfo.setUserName(name);
        }
        
        // 设置服务名称和服务器IP
        logInfo.setServiceName(serviceName);
        logInfo.setServerIp(serverIp);
        
        auditLogProcessor.process(logInfo);
    }
    
    public String extractUserName(Authentication authentication) {
        String name = authentication.getName();
        final String username = OAuth2TokenIntrospectionClaimNames.USERNAME;
        switch (authentication) {
            case BearerTokenAuthentication bearerAuth ->
                    name = String.valueOf(bearerAuth.getTokenAttributes().get(username));
            case JwtAuthenticationToken jwtToken -> name = jwtToken.getToken().getClaimAsString(username);
            case OAuth2AccessTokenAuthenticationToken oauthToken -> {
                Map<String, Object> parameters = oauthToken.getAdditionalParameters();
                Object object = parameters.get(username);
                if (object != null) {
                    name = String.valueOf(object);
                }
            }
            default -> {
            }
        }
        return name;
    }
    
    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // For multiple proxies, the first IP is the real client IP.
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0];
        }
        return ip;
    }
    
    @Async
    public void recordAuditLog(HttpServletRequest request,
                               String userName,
                               String desc,
                               boolean success,
                               String exception) {
        AuditLogInfo logInfo = new AuditLogInfo();
        logInfo.setUserName(userName);
        logInfo.setDescription(desc);
        logInfo.setOperationTime(new Date());
        logInfo.setSuccess(success);
        logInfo.setExceptionDetails(exception);
        if (request != null) {
            logInfo.setRequestIp(getIpAddress(request));
            logInfo.setRequestUri(request.getRequestURI());
            logInfo.setRequestMethod(request.getMethod());
        }
        
        // 设置服务名称和服务器IP
        logInfo.setServiceName(serviceName);
        logInfo.setServerIp(serverIp);
        
        auditLogProcessor.process(logInfo);
    }
}