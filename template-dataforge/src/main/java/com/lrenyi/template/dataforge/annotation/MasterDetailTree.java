package com.lrenyi.template.dataforge.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;

/**
 * 左树右表布局的树侧配置，用于 {@link EntityUiLayout}。
 */
@Documented
@Target({TYPE, ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MasterDetailTree {

    /** 左侧树使用的实体 pathSegment，如 departments */
    String treeEntity() default "";

    /** 树实体显示名（可选，用于前端标题等） */
    String treeEntityLabel() default "";

    /** 树实体主键字段名 */
    String treeIdField() default "id";

    /** 树形父字段名 */
    String treeParentField() default "parentId";

    /** 树节点展示字段名 */
    String treeLabelField() default "name";

    /** 树节点排序字段名 */
    String treeSortField() default "sortOrder";

    /** 右侧主表中关联左树的字段名，如 departmentId */
    String relationField() default "";

    /** 页面初始化时根节点/右表行为 */
    RootSelectionMode rootSelectionMode() default RootSelectionMode.ALL;

    /** 点击树节点时是否包含其子孙节点下的数据 */
    boolean includeDescendants() default false;

    /** 是否从右侧搜索栏隐藏关联字段 */
    boolean hideTableSearchRelationField() default true;
}
