package com.lrenyi.template.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Function {
    
    /**
     * @return 属于哪个服务
     */
    String service();
    
    /**
     * @return 属于哪个域对象
     */
    String domain();
    
    /**
     * @return 接口名称
     */
    String interfaceName();
}
