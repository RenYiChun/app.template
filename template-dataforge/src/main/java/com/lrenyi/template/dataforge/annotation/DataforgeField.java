package com.lrenyi.template.dataforge.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段元数据注解，用于定义字段在UI和业务上的属性。
 * 整合了原{@code @Searchable}的元数据部分。
 * <p>
 * 字段级别：标注在字段上；类级别：配合 {@code parentFieldName} 覆盖父类字段的元数据。
 * </p>
 */
@Documented
@Repeatable(DataforgeField.List.class)
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataforgeField {
    
    /**
     * 父类字段名（仅在类级别使用时有效）。
     * 当标注在实体类上时，用于指定要覆盖元数据的父类字段。
     */
    String parentFieldName() default "";
    
    /**
     * 字段显示标签（UI Label），默认取字段名。
     * 原{@code @Searchable.label}
     */
    String label() default "";
    
    /**
     * 字段描述信息，用于tooltip或表单提示。
     */
    String description() default "";
    
    /**
     * 字段分组，用于表单分组显示。
     */
    String group() default "";
    
    /**
     * 组内排序权重。
     */
    int groupOrder() default 0;
    
    /**
     * 表格列排序权重，值越小越靠前。
     */
    int columnOrder() default 0;
    
    /**
     * 表单字段排序权重，值越小越靠前。
     */
    int formOrder() default 0;
    
    // ==================== 表格列配置 ====================
    
    /**
     * 是否在表格中显示该列。
     */
    boolean columnVisible() default true;
    
    /**
     * 是否支持调整列宽。
     */
    boolean columnResizable() default true;
    
    /**
     * 是否支持表格列排序（用户点击表头排序）。
     */
    boolean columnSortable() default true;
    
    /**
     * 是否支持列过滤。
     */
    boolean columnFilterable() default false;
    
    /**
     * 列对齐方式。
     */
    ColumnAlign columnAlign() default ColumnAlign.LEFT;
    
    /**
     * 列宽度（像素），0表示自动宽度。
     */
    int columnWidth() default 0;
    
    /**
     * 最小列宽度（像素）。
     */
    int columnMinWidth() default 80;
    
    /**
     * 列固定方式。
     */
    ColumnFixed columnFixed() default ColumnFixed.NONE;
    
    /**
     * 文本溢出时是否显示省略号。
     */
    boolean columnEllipsis() default false;
    
    /**
     * 自定义列样式类。
     */
    String columnClassName() default "";
    
    // ==================== 表单控件配置 ====================
    
    /**
     * 表单组件类型。
     */
    FormComponent component() default FormComponent.TEXT;
    
    /**
     * 占位符文本。
     */
    String placeholder() default "";
    
    /**
     * 提示信息。
     */
    String tips() default "";
    
    /**
     * 是否必填（UI层面）。
     */
    boolean required() default false;
    
    /**
     * 是否只读。
     */
    boolean readonly() default false;
    
    /**
     * 是否禁用。
     */
    boolean disabled() default false;
    
    /**
     * 是否隐藏。
     */
    boolean hidden() default false;
    
    // ==================== 验证规则 ====================
    
    /**
     * 正则表达式验证。
     */
    String regex() default "";
    
    /**
     * 正则验证失败提示信息。
     */
    String regexMessage() default "";
    
    /**
     * 最小长度。
     */
    int minLength() default 0;
    
    /**
     * 最大长度。
     */
    int maxLength() default 0;
    
    /**
     * 最小值。
     */
    double minValue() default Double.MIN_VALUE;
    
    /**
     * 最大值。
     */
    double maxValue() default Double.MAX_VALUE;
    
    /**
     * 允许的值列表。
     */
    String[] allowedValues() default {};
    
    // ==================== 数据字典/枚举 ====================
    
    /**
     * 数据字典编码。
     */
    String dictCode() default "";
    
    /**
     * 枚举选项值（当没有Enum类时使用）。
     */
    String[] enumOptions() default {};
    
    /**
     * 枚举选项标签（与enumOptions对应）。
     */
    String[] enumLabels() default {};
    
    // ==================== 搜索配置 ====================
    
    /**
     * 是否作为搜索条件。
     * 原{@code @Searchable}核心逻辑。
     */
    boolean searchable() default false;
    
    /**
     * 搜索栏排序权重，值越小越靠前。
     */
    int searchOrder() default 0;
    
    /**
     * 搜索类型。
     */
    SearchType searchType() default SearchType.EQUALS;
    
    /**
     * 搜索组件类型。
     */
    SearchComponent searchComponent() default SearchComponent.INPUT;
    
    /**
     * 搜索默认值。
     */
    String searchDefaultValue() default "";
    
    /**
     * 搜索是否必填。
     */
    boolean searchRequired() default false;
    
    /**
     * 搜索占位符。
     */
    String searchPlaceholder() default "";
    
    /**
     * 范围搜索字段对（如["startTime","endTime"]）。
     */
    String[] searchRangeFields() default {};
    
    // ==================== 数据转换与显示 ====================
    
    /**
     * 格式化模式（日期、数字等）。
     */
    String format() default "";
    
    /**
     * 脱敏规则（如手机号：*******1234）。
     */
    String maskPattern() default "";
    
    /**
     * 脱敏类型。
     */
    MaskType maskType() default MaskType.NONE;
    
    /**
     * 是否敏感数据（需要加密存储）。
     */
    boolean sensitive() default false;
    
    /**
     * 默认值（字符串形式）。
     */
    String defaultValue() default "";
    
    /**
     * 默认值表达式（SpEL）。
     */
    String defaultValueExpression() default "";
    
    // ==================== 关联关系 ====================
    
    /**
     * 是否为外键关联字段。
     */
    boolean foreignKey() default false;
    
    /**
     * 关联实体类名。
     */
    String referencedEntity() default "";
    
    /**
     * 关联字段名。
     */
    String referencedField() default "id";
    
    /**
     * 显示字段名（用于下拉框显示）。
     */
    String displayField() default "name";
    
    /**
     * 值字段名（用于下拉框值）。
     */
    String valueField() default "id";
    
    /**
     * 是否懒加载关联数据。
     */
    boolean lazyLoad() default true;
    
    /**
     * 用于支持 {@code @DataforgeField} 的重复注解（类级别覆盖多个父类字段时使用）。
     */
    @Documented
    @Target({ElementType.FIELD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        DataforgeField[] value();
    }
}