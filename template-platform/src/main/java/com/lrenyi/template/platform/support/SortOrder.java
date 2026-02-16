package com.lrenyi.template.platform.support;

/**
 * 排序项：字段名与方向（asc/desc）。
 */
public record SortOrder(String field, String dir) {
}
