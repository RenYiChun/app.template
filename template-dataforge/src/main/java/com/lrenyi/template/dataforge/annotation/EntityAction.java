package com.lrenyi.template.dataforge.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * 标记实体扩展 Action，用于注册到 ActionRegistry。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EntityAction {
    
    /**
     * 所属实体类。
     */
    Class<?> entity();
    
    /**
     * 动作名，对应 URL 最后一段，如 resetPassword。
     */
    String actionName();
    
    /**
     * HTTP 方法，默认 POST。
     */
    RequestMethod method() default RequestMethod.POST;
    
    /**
     * 请求体类型，无 body 时用 Void 或 EmptyRequest。
     */
    Class<?> requestType() default Void.class;
    
    /**
     * 响应类型。
     */
    Class<?> responseType() default Void.class;
    
    /**
     * 简短描述，用于 OpenAPI summary。
     */
    String summary() default "";
    
    /**
     * 详细描述。
     */
    String description() default "";
    
    /**
     * 所需权限标识，可多个。
     */
    String[] permissions() default {};
    
    /**
     * 该 Action 是否需要实体 ID。
     * 默认为 true。若为 false，OpenAPI 文档将只生成不带 ID 的接口路径（/api/{entity}/{actionName}）。
     */
    boolean requireId() default true;
}
