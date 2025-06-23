package com.lrenyi.template.api.audit.aspect;

import com.lrenyi.template.api.audit.service.AuditLogService;
import com.lrenyi.template.api.config.FeignClientConfiguration;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
public class AuditLogAspect {
    
    private final AuditLogService auditLogService;
    
    public AuditLogAspect(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }
    
    //@formatter:off
    @Pointcut("@within(org.springframework.stereotype.Controller) || @within(org.springframework.web.bind.annotation.RestController)")
    public void auditLogPointcut() {
    }
    //@formatter:on
    
    @Around("auditLogPointcut()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = null;
        if (attributes != null) {
            request = attributes.getRequest();
            String internalCallHeader = request.getHeader(FeignClientConfiguration.HEADER_NAME);
            if ("true".equalsIgnoreCase(internalCallHeader)) {
                // This is an internal call from another service, skip logging.
                return joinPoint.proceed();
            }
        }
        long startTime = System.currentTimeMillis();
        Object result;
        SecurityContext context = SecurityContextHolder.getContext();
        try {
            result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - startTime;
            auditLogService.saveLog(joinPoint, request, context, executionTime, null);
            return result;
        } catch (Throwable e) {
            long executionTime = System.currentTimeMillis() - startTime;
            auditLogService.saveLog(joinPoint, request, context, executionTime, e);
            throw e;
        }
    }
}