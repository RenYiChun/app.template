package com.lrenyi.template.dataforge.audit.service;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Map;
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
    private final String serverIp;

    public AuditLogService(AuditLogProcessor auditLogProcessor, String serviceName,
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
            return "unknown";
        }
    }

    @Async
    public void saveLog(ProceedingJoinPoint joinPoint, String ipAddress, String uri, String httpMethod,
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
        String description = null;
        AuditDescriptionResolver resolver = descriptionResolverProvider.getIfAvailable();
        if (resolver != null) {
            HttpServletRequest request = null;
            try {
                var attrs = RequestContextHolder.getRequestAttributes();
                if (attrs instanceof ServletRequestAttributes servletAttrs) {
                    request = servletAttrs.getRequest();
                }
            } catch (Exception ignored) {
                // ignore
            }
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
                String methodName = signature.getName();
                description = className + "#" + methodName;
            }
        }
        logInfo.setDescription(description);
        logInfo.setRequestIp(ipAddress);
        logInfo.setRequestUri(uri);
        logInfo.setRequestMethod(httpMethod);
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

        AuditLog auditLogAnnotation = method.getAnnotation(AuditLog.class);
        if (auditLogAnnotation != null) {
            if (StringUtils.hasText(auditLogAnnotation.reason())) {
                logInfo.setReason(auditLogAnnotation.reason());
            }
            if (StringUtils.hasText(auditLogAnnotation.targetType())) {
                logInfo.setTargetType(auditLogAnnotation.targetType());
            }
        }

        HttpServletRequest requestForEnricher = null;
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletAttrs) {
                requestForEnricher = servletAttrs.getRequest();
            }
        } catch (Exception ignored) {
            // ignore
        }
        final HttpServletRequest request = requestForEnricher;
        if (request != null && enricherProvider != null) {
            enricherProvider.orderedStream().forEach(enricher -> enricher.enrich(joinPoint, request, logInfo));
        }

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
            default -> {
                String fromOAuth2AuthServer = extractUsernameFromOAuth2AccessToken(authentication);
                if (fromOAuth2AuthServer != null) {
                    name = fromOAuth2AuthServer;
                }
            }
        }
        return name;
    }
    
    private static String extractUsernameFromOAuth2AccessToken(Authentication authentication) {
        try {
            Class<?> clazz = Class.forName("org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken");
            if (!clazz.isInstance(authentication)) {
                return null;
            }
            Object params = clazz.getMethod("getAdditionalParameters").invoke(authentication);
            if (params instanceof Map<?, ?> map) {
                Object v = map.get("username");
                return v != null ? String.valueOf(v) : null;
            }
        } catch (Exception ignored) {
            // authorization-server not on classpath or other failure
        }
        return null;
    }

    public String getIpAddress(HttpServletRequest request) {
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
        recordAuditLog(request, userName, desc, success, exception, null, null, null, null);
    }

    @Async
    public void recordAuditLog(HttpServletRequest request,
                               String userName,
                               String desc,
                               boolean success,
                               String exception,
                               String reason,
                               String targetType,
                               String targetId,
                               Long affectedCount) {
        AuditLogInfo logInfo = new AuditLogInfo();
        logInfo.setUserName(userName);
        logInfo.setDescription(desc);
        logInfo.setOperationTime(new Date());
        logInfo.setSuccess(success);
        logInfo.setExceptionDetails(exception);
        if (StringUtils.hasText(reason)) {
            logInfo.setReason(reason);
        }
        if (StringUtils.hasText(targetType)) {
            logInfo.setTargetType(targetType);
        }
        if (StringUtils.hasText(targetId)) {
            logInfo.setTargetId(targetId);
        }
        if (affectedCount != null) {
            logInfo.setAffectedCount(affectedCount);
        }
        if (request != null) {
            logInfo.setRequestIp(getIpAddress(request));
            logInfo.setRequestUri(request.getRequestURI());
            logInfo.setRequestMethod(request.getMethod());
        }
        logInfo.setServiceName(serviceName);
        logInfo.setServerIp(serverIp);
        auditLogProcessor.process(logInfo);
    }
}
