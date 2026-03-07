package com.lrenyi.template.dataforge.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import com.lrenyi.template.dataforge.support.ImportValueConverter;

/**
 * 字段导入配置注解。
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataforgeImport {
    
    /**
     * 是否允许导入。
     */
    boolean enabled() default true;
    
    /**
     * 导入是否必填。
     */
    boolean required() default false;
    
    /**
     * 示例数据（用于导入模板）。
     */
    String sample() default "";
    
    /**
     * 导入值转换器。
     */
    Class<? extends ImportValueConverter> converter() default ImportValueConverter.class;
    
    /**
     * 导入验证正则表达式。
     */
    String validationRegex() default "";
    
    /**
     * 验证失败提示信息。
     */
    String validationMessage() default "";
    
    /**
     * 导入默认值（当单元格为空时使用）。
     */
    String defaultValue() default "";
    
    /**
     * 是否唯一（用于去重校验）。
     */
    boolean unique() default false;
    
    /**
     * 重复数据提示信息。
     */
    String duplicateMessage() default "数据重复";
    
    /**
     * 数据字典编码（用于验证导入值是否在字典中）。
     */
    String dictCode() default "";
    
    /**
     * 允许的值列表。
     */
    String[] allowedValues() default {};
    
    /**
     * 最小值（用于数字验证）。
     */
    double minValue() default Double.MIN_VALUE;
    
    /**
     * 最大值（用于数字验证）。
     */
    double maxValue() default Double.MAX_VALUE;
    
    /**
     * 最小长度（用于字符串验证）。
     */
    int minLength() default 0;
    
    /**
     * 最大长度（用于字符串验证）。
     */
    int maxLength() default 0;
    
    /**
     * 日期格式（用于日期验证）。
     */
    String dateFormat() default "";
    
    /**
     * 是否忽略大小写。
     */
    boolean ignoreCase() default false;
    
    /**
     * 是否去除空格。
     */
    boolean trim() default true;
    
    /**
     * 错误处理策略：
     * STOP-停止导入，SKIP-跳过当前行，USE_DEFAULT-使用默认值。
     */
    ErrorPolicy errorPolicy() default ErrorPolicy.STOP;
    
    /**
     * 错误处理策略枚举。
     */
    enum ErrorPolicy {
        STOP,
        SKIP,
        USE_DEFAULT
    }
}