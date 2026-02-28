package com.lrenyi.template.dataforge.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * DTO控制注解，支持字段级别和类级别使用。
 * 整合并扩展了原{@code @DtoExcludeFrom}的功能。
 *
 * <p>字段级别使用：控制单个字段参与哪些DTO的生成。</p>
 * <p>类级别使用：控制父类字段覆盖和全局DTO生成策略。</p>
 */
@Documented
@Repeatable(DataforgeDto.List.class)
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataforgeDto {
    
    // ==================== 字段级别属性 ====================
    
    /**
     * 包含在哪些DTO类型中（空数组表示包含在相关DTO中）。
     * 与{@code exclude()}互斥，优先使用{@code exclude()}。
     */
    DtoType[] include() default {};
    
    /**
     * 从哪些DTO类型中排除。
     * 原{@code @DtoExcludeFrom.value}
     */
    DtoType[] exclude() default {};
    
    /**
     * DTO中的字段名（默认同实体字段名）。
     */
    String fieldName() default "";
    
    /**
     * DTO中的字段类型（默认同实体字段类型）。
     */
    Class<?> fieldType() default Object.class;
    
    /**
     * 字段值转换器。
     */
    Class<?> converter() default Object.class;
    
    /**
     * 格式化模式（用于DTO序列化）。
     */
    String format() default "";
    
    /**
     * 验证组（用于分组验证）。
     */
    Class<?>[] validationGroups() default {};
    
    // ==================== 快捷属性 ====================
    
    /**
     * 只读字段（等价于{@code exclude = {DtoType.CREATE, DtoType.UPDATE, DtoType.BATCH_CREATE, DtoType.BATCH_UPDATE}}）。
     */
    boolean readOnly() default false;
    
    /**
     * 只写字段（等价于{@code exclude = DtoType.RESPONSE}）。
     */
    boolean writeOnly() default false;
    
    /**
     * 仅创建时使用（等价于{@code include = {DtoType.CREATE, DtoType.BATCH_CREATE}}）。
     */
    boolean createOnly() default false;
    
    /**
     * 仅更新时使用（等价于{@code include = {DtoType.UPDATE, DtoType.BATCH_UPDATE}}）。
     */
    boolean updateOnly() default false;
    
    /**
     * 仅查询时使用（等价于{@code include = DtoType.QUERY}）。
     */
    boolean queryOnly() default false;
    
    // ==================== 类级别属性 ====================
    
    /**
     * 父类字段名（仅在{@code parentOverrides}中使用）。
     * 当标注在类上时，用于指定要覆盖的父类字段。
     */
    String parentFieldName() default "";
    
    /**
     * 全局忽略的字段列表（仅在类级别使用）。
     */
    String[] ignoreFields() default {};
    
    /**
     * DTO包名（默认：实体包名 + ".dto"）。
     */
    String dtoPackage() default "";
    
    /**
     * DTO后缀。
     */
    String dtoSuffix() default "DTO";
    
    /**
     * 是否生成所有类型DTO。
     */
    boolean generateAll() default true;
    
    /**
     * 指定生成的DTO类型（当{@code generateAll=false}时生效）。
     */
    DtoType[] generateTypes() default {DtoType.CREATE, DtoType.UPDATE, DtoType.RESPONSE, DtoType.QUERY,
            DtoType.PAGE_RESPONSE};
    
    /**
     * API摘要。
     */
    String apiSummary() default "";
    
    /**
     * API详细描述。
     */
    String apiDescription() default "";
    
    /**
     * API标签。
     */
    String[] apiTags() default {};
    
    /**
     * 是否生成OpenAPI文档。
     */
    boolean openApi() default true;
    
    /**
     * 示例值。
     */
    String example() default "";
    
    /**
     * 是否已废弃。
     */
    boolean deprecated() default false;
    
    // ==================== 容器注解 ====================
    
    /**
     * 用于支持{@code @DataforgeDto}的重复注解。
     */
    @Documented
    @Target({ElementType.FIELD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        DataforgeDto[] value();
    }
}