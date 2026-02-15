package com.lrenyi.template.entityplatform.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在实体字段上，表示该字段不参与指定的 DTO 生成。
 * 例如：密码字段加 {@code @DtoExcludeFrom(DtoType.RESPONSE)} 则不出现在 ResponseDTO 中，避免响应里带出密码。
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DtoExcludeFrom {

    /**
     * 从哪些 DTO 类型中排除该字段。空数组表示不排除（参与所有 DTO）。
     */
    DtoType[] value() default {};
}
