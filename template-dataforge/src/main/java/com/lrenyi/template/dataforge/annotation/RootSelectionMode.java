package com.lrenyi.template.dataforge.annotation;

/**
 * 左树右表布局下，页面初始化时根节点/右表数据的处理方式。
 */
public enum RootSelectionMode {
    /** 默认显示全部数据，用户点击树节点后再按节点过滤 */
    ALL,
    /** 不自动选中任何节点，右表不请求或显示空，直到用户选择树节点 */
    NONE
}
