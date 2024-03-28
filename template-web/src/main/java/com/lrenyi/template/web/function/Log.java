package com.lrenyi.template.web.function;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.METHOD})
public @interface Log {
    
    /**
     * 操作的对象是什么
     */
    String object();
    
    /**
     * 对操作的对象进行的什么操作
     */
    String operation();
    
    /**
     * 是否保存请求的参数
     */
    boolean dataSave() default true;
}
