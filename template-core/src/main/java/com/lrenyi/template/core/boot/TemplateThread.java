package com.lrenyi.template.core.boot;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TemplateThread {
    
    String name() default "";
    
    boolean daemon() default false;
    
    boolean virtually() default true;
    
    int priority() default -1;
    
    long timeOut() default -1;
    
    TimeUnit unit() default TimeUnit.SECONDS;
}
