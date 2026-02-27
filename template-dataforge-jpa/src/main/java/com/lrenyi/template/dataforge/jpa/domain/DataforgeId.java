package com.lrenyi.template.dataforge.jpa.domain;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.hibernate.annotations.IdGeneratorType;

/**
 * 平台实体主键生成注解，替代已弃用的 {@code @GenericGenerator}。
 * 使用 {@link DataforgeIdGenerator} 按主键类型分发生成策略。
 *
 * @see DataforgeIdGenerator
 */
@IdGeneratorType(DataforgeIdGenerator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface DataforgeId {
}
