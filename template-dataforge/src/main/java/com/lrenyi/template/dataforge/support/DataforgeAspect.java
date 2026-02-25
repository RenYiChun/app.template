package com.lrenyi.template.dataforge.support;

import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

/**
 * 统一切面：记录实体平台 Controller CRUD 操作的耗时和错误指标。
 */
@Aspect
@Order(100)
public final class DataforgeAspect {

    private static final Logger log = LoggerFactory.getLogger(DataforgeAspect.class);

    private final MeterRegistry meterRegistry;

    public DataforgeAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Around("execution(* com.lrenyi.template.dataforge.controller.GenericEntityController.*(..))")
    public Object logRequest(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().getName();
        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            long elapsed = System.currentTimeMillis() - start;
            Timer.builder("app.template.dataforge.request.duration")
                 .tag("method", method)
                 .register(meterRegistry)
                 .record(elapsed, TimeUnit.MILLISECONDS);
            return result;
        } catch (Throwable t) {
            long elapsed = System.currentTimeMillis() - start;
            Timer.builder("app.template.dataforge.request.duration")
                 .tag("method", method)
                 .register(meterRegistry)
                 .record(elapsed, TimeUnit.MILLISECONDS);
            Counter.builder("app.template.dataforge.request.errors")
                   .tag("method", method)
                   .tag("errorType", t.getClass().getSimpleName())
                   .register(meterRegistry).increment();
            throw t;
        }
    }
}
