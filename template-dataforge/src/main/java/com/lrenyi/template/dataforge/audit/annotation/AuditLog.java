package com.lrenyi.template.dataforge.audit.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    /**
     * 操作描述（What）
     */
    String description() default "";

    /**
     * 操作原因/业务意图（Why），如「根据工单 #123 执行」
     */
    String reason() default "";

    /**
     * 操作对象类型（What 结构化），如 User、Order
     */
    String targetType() default "";
}
