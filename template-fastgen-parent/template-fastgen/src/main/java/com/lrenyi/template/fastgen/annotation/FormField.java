package com.lrenyi.template.fastgen.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 表单/列表字段描述，用于生成表单控件与表格列。
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface FormField {

    /**
     * 显示标签。
     */
    String label() default "";

    /**
     * 控件类型：text、password、email、checkbox、number、date 等。
     */
    String type() default "text";

    /**
     * 是否必填。
     */
    boolean required() default false;

    /**
     * 在列表页是否显示该列。
     */
    boolean listable() default true;

    /**
     * 在表单页是否可编辑。
     */
    boolean editable() default true;
}
