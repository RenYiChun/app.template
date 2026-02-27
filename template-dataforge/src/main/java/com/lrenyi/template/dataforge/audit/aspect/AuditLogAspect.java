package com.lrenyi.template.dataforge.audit.aspect;

import com.lrenyi.template.core.util.TemplateConstant;
import com.lrenyi.template.dataforge.audit.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@ConditionalOnExpression("'${app.template.enabled:true}' == 'true' && '${app.template.audit.enabled:false}' == 'true'")
public class AuditLogAspect {

    private final AuditLogService auditLogService;

    public AuditLogAspect(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Pointcut("""
        execution(public * *(..))
        && (@within(org.springframework.stereotype.Controller)
        || @within(org.springframework.web.bind.annotation.RestController))
          && (@annotation(org.springframework.web.bind.annotation.RequestMapping)
           || @annotation(org.springframework.web.bind.annotation.GetMapping)
            || @annotation(org.springframework.web.bind.annotation.PostMapping)
             || @annotation(org.springframework.web.bind.annotation.PutMapping)
              || @annotation(org.springframework.web.bind.annotation.DeleteMapping)
               || @annotation(org.springframework.web.bind.annotation.PatchMapping)
             )
    """)
    public void auditLogPointcut() {
    }

    @Around("auditLogPointcut()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = null;
        if (attributes != null) {
            request = attributes.getRequest();
            if ("true".equalsIgnoreCase(request.getHeader(TemplateConstant.HEADER_NAME))) {
                return joinPoint.proceed();
            }
        }
        if (request == null) {
            return joinPoint.proceed();
        }
        long startTime = System.currentTimeMillis();
        Object result;
        SecurityContext context = SecurityContextHolder.getContext();
        String ipAddress = auditLogService.getIpAddress(request);
        String uri = request.getRequestURI();
        String method = request.getMethod();
        try {
            result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - startTime;
            auditLogService.saveLog(joinPoint, ipAddress, uri, method, context, executionTime, null);
            return result;
        } catch (Throwable e) {
            long executionTime = System.currentTimeMillis() - startTime;
            auditLogService.saveLog(joinPoint, ipAddress, uri, method, context, executionTime, e);
            throw e;
        }
    }
}
