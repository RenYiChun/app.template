package com.lrenyi.template.dataforge.support;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

/**
 * 统一日志：记录实体平台 Controller 请求。
 */
@Aspect
@Order(100)
public final class DataforgeAspect {

    private static final Logger log = LoggerFactory.getLogger(DataforgeAspect.class);

    @Around("execution(* com.lrenyi.template.dataforge.controller.GenericEntityController.*(..))")
    public Object logRequest(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().getName();
        Object[] args = pjp.getArgs();
        log.debug("Dataforge request: method={}, argsCount={}", method, args != null ? args.length : 0);
        try {
            Object result = pjp.proceed();
            log.debug("Dataforge response: method={}", method);
            return result;
        } catch (Throwable t) {
            log.warn("Dataforge error: method={}, error={}", method, t.getMessage());
            throw t;
        }
    }
}
