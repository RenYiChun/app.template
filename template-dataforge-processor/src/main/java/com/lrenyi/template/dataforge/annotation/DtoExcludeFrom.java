package com.lrenyi.template.dataforge.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @deprecated 已废弃，请使用 {@link DataforgeDto} 注解的 {@code exclude} 属性。
 * 例如：{@code @DataforgeDto(exclude = DtoType.RESPONSE)}
 * 
 * 标注在实体字段上，表示该字段不参与指定的 DTO 生成。
 * 例如：密码字段加 {@code @DtoExcludeFrom(DtoType.RESPONSE)} 则不出现在 ResponseDTO 中，避免响应里带出密码。
 */
@Deprecated(since = "2.4.3", forRemoval = true)
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DtoExcludeFrom {

    /**
     * 从哪些 DTO 类型中排除该字段。空数组表示不排除（参与所有 DTO）。
     */
    DtoType[] value() default {};
}
