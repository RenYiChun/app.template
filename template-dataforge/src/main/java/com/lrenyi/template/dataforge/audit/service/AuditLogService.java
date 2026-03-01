package com.lrenyi.template.dataforge.audit.service;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import com.lrenyi.template.dataforge.audit.annotation.AuditLog;
import com.lrenyi.template.dataforge.audit.enricher.AuditLogEnricher;
import com.lrenyi.template.dataforge.audit.model.AuditLogInfo;
import com.lrenyi.template.dataforge.audit.processor.AuditLogProcessor;
import com.lrenyi.template.dataforge.audit.resolver.AuditDescriptionResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
public class AuditLogService {
    
    private final AuditLogProcessor auditLogProcessor;
    private final String serviceName;
    private final ObjectProvider<AuditDescriptionResolver> descriptionResolverProvider;
    private final ObjectProvider<AuditLogEnricher> enricherProvider;
    private static final String UNKNOWN = "unknown";
    private final String serverIp;
    
    private AuditLogService self;
    
    @Autowired
    public void setSelf(@Lazy AuditLogService self) {
        this.self = self;
    }
    
    public AuditLogService(AuditLogProcessor auditLogProcessor,
            String serviceName,
            ObjectProvider<AuditDescriptionResolver> descriptionResolverProvider,
            ObjectProvider<AuditLogEnricher> enricherProvider) {
        this.auditLogProcessor = auditLogProcessor;
        this.serviceName = serviceName;
        this.descriptionResolverProvider = descriptionResolverProvider;
        this.enricherProvider = enricherProvider;
        this.serverIp = getServerIp();
    }
    
    private String getServerIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.warn("failed to get server IP address", e);
            return UNKNOWN;
        }
    }
    
    @Async
    public void saveLog(ProceedingJoinPoint joinPoint,
            String ipAddress,
            String uri,
            String httpMethod,
            SecurityContext context,
            long time,
            Throwable e) {
        AuditLogInfo logInfo = new AuditLogInfo();
        logInfo.setExecutionTimeMs(time);
        logInfo.setOperationTime(new Date());
        
        logInfo.setSuccess(e == null);
        if (e != null) {
            logInfo.setExceptionDetails(e.getMessage());
        }
        
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String description = resolveDescription(joinPoint, method);
        logInfo.setDescription(description);
        logInfo.setRequestIp(ipAddress);
        logInfo.setRequestUri(uri);
        logInfo.setRequestMethod(httpMethod);
        Authentication authentication = context.getAuthentication();
        String name = extractUserName(authentication);
        if (!StringUtils.hasLength(name)) {
            log.warn("not find user info, the type of Authentication is: {}", authentication.getClass().getName());
        }
        logInfo.setUserName(name);
        
        fillAnnotationInfo(logInfo, method);
        
        enrichLogInfo(joinPoint, logInfo);
        
        logInfo.setServiceName(serviceName);
        logInfo.setServerIp(serverIp);
        
        auditLogProcessor.process(logInfo);
    }
    
    private String resolveDescription(ProceedingJoinPoint joinPoint, Method method) {
        String description = null;
        AuditDescriptionResolver resolver = descriptionResolverProvider.getIfAvailable();
        if (resolver != null) {
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                description = resolver.resolve(joinPoint, request);
            }
        }
        if (description == null || description.isEmpty()) {
            AuditLog auditLogAnnotation = method.getAnnotation(AuditLog.class);
            if (auditLogAnnotation != null) {
                description = auditLogAnnotation.description();
            } else {
                String className = joinPoint.getTarget().getClass().getSimpleName();
                String methodName = method.getName();
                description = className + "#" + methodName;
            }
        }
        return description;
    }
    
    private HttpServletRequest getCurrentRequest() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletAttrs) {
                return servletAttrs.getRequest();
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }
    
    private void fillAnnotationInfo(AuditLogInfo logInfo, Method method) {
        AuditLog auditLogAnnotation = method.getAnnotation(AuditLog.class);
        if (auditLogAnnotation != null) {
            if (StringUtils.hasText(auditLogAnnotation.reason())) {
                logInfo.setReason(auditLogAnnotation.reason());
            }
            if (StringUtils.hasText(auditLogAnnotation.targetType())) {
                logInfo.setTargetType(auditLogAnnotation.targetType());
            }
        }
    }
    
    private void enrichLogInfo(ProceedingJoinPoint joinPoint, AuditLogInfo logInfo) {
        HttpServletRequest request = getCurrentRequest();
        if (request != null && enricherProvider != null) {
            enricherProvider.orderedStream().forEach(enricher -> enricher.enrich(joinPoint, request, logInfo));
        }
    }
    
    public String extractUserName(Authentication authentication) {
        String name = authentication.getName();
        final String username = OAuth2TokenIntrospectionClaimNames.USERNAME;
        if (authentication instanceof BearerTokenAuthentication bearerAuth) {
            Object attr = bearerAuth.getTokenAttributes().get(username);
            if (attr != null) {
                name = String.valueOf(attr);
            }
        } else if (authentication instanceof JwtAuthenticationToken jwtToken) {
            String claimAsString = jwtToken.getToken().getClaimAsString(username);
            if (StringUtils.hasText(claimAsString)) {
                name = claimAsString;
            }
        }
        return name;
    }
    
    @Async
    public void recordAuditLog(HttpServletRequest request,
            String userName,
            String desc,
            boolean success,
            String exception) {
        self.recordAuditLog(RecordParams.builder()
                .request(request)
                .userName(userName)
                .desc(desc)
                .success(success)
                .exception(exception)
                .build());
    }

    /**
     * 参数对象，用于减少 recordAuditLog 方法参数数量。
     */
    public static final class RecordParams {
        private final HttpServletRequest request;
        private final String userName;
        private final String desc;
        private final boolean success;
        private final String exception;
        private final String reason;
        private final String targetType;
        private final String targetId;
        private final Long affectedCount;

        private RecordParams(Builder b) {
            this.request = b.request;
            this.userName = b.userName;
            this.desc = b.desc;
            this.success = b.success;
            this.exception = b.exception;
            this.reason = b.reason;
            this.targetType = b.targetType;
            this.targetId = b.targetId;
            this.affectedCount = b.affectedCount;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            HttpServletRequest request;
            String userName;
            String desc;
            boolean success;
            String exception;
            String reason;
            String targetType;
            String targetId;
            Long affectedCount;

            public Builder request(HttpServletRequest v) { request = v; return this; }
            public Builder userName(String v) { userName = v; return this; }
            public Builder desc(String v) { desc = v; return this; }
            public Builder success(boolean v) { success = v; return this; }
            public Builder exception(String v) { exception = v; return this; }
            public Builder reason(String v) { reason = v; return this; }
            public Builder targetType(String v) { targetType = v; return this; }
            public Builder targetId(String v) { targetId = v; return this; }
            public Builder affectedCount(Long v) { affectedCount = v; return this; }

            public RecordParams build() {
                return new RecordParams(this);
            }
        }
    }

    @Async
    public void recordAuditLog(RecordParams params) {
        AuditLogInfo logInfo = new AuditLogInfo();
        logInfo.setUserName(params.userName);
        logInfo.setDescription(params.desc);
        logInfo.setOperationTime(new Date());
        logInfo.setSuccess(params.success);
        logInfo.setExceptionDetails(params.exception);
        if (StringUtils.hasText(params.reason)) {
            logInfo.setReason(params.reason);
        }
        if (StringUtils.hasText(params.targetType)) {
            logInfo.setTargetType(params.targetType);
        }
        if (StringUtils.hasText(params.targetId)) {
            logInfo.setTargetId(params.targetId);
        }
        if (params.affectedCount != null) {
            logInfo.setAffectedCount(params.affectedCount);
        }
        if (params.request != null) {
            logInfo.setRequestIp(getIpAddress(params.request));
            logInfo.setRequestUri(params.request.getRequestURI());
            logInfo.setRequestMethod(params.request.getMethod());
        }
        logInfo.setServiceName(serviceName);
        logInfo.setServerIp(serverIp);
        auditLogProcessor.process(logInfo);
    }
    
    private static final String[] IP_HEADERS =
            {"x-forwarded-for", "Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"};
    
    public String getIpAddress(HttpServletRequest request) {
        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !UNKNOWN.equalsIgnoreCase(ip)) {
                return parseIp(ip);
            }
        }
        return parseIp(request.getRemoteAddr());
    }
    
    private String parseIp(String ip) {
        if (ip != null && ip.contains(",")) {
            return ip.split(",")[0];
        }
        return ip;
    }
}
