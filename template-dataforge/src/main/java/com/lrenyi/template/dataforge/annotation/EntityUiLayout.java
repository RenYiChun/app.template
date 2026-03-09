package com.lrenyi.template.dataforge.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;

/**
 * 实体列表页 UI 布局配置，用于 {@link DataforgeEntity}。
 */
@Documented
@Target({TYPE, ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface EntityUiLayout {

    /** 布局模式，默认普通表格 */
    UiLayoutMode mode() default UiLayoutMode.TABLE;

    /** 左树右表时的树侧配置，仅当 mode=MASTER_DETAIL_TREE 时必填 */
    MasterDetailTree masterDetailTree() default @MasterDetailTree;
}
