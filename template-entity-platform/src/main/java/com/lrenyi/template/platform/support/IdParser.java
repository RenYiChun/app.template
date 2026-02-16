package com.lrenyi.template.platform.support;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 将 HTTP 层传入的 id 字符串或 JSON 值按主键类型解析为 Object（Long、String、UUID 等）。
 */
public final class IdParser {

    private IdParser() {
    }

    /**
     * 将字符串解析为主键类型对应的对象。
     *
     * @param idStr          路径或请求体中的 id 字符串，非 null
     * @param primaryKeyType 主键类型，如 Long.class、String.class、UUID.class
     * @return 解析后的主键值
     * @throws IllegalArgumentException 类型不支持或格式错误
     */
    public static Object parseId(String idStr, Class<?> primaryKeyType) {
        if (idStr == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        String trimmed = idStr.trim();
        if (primaryKeyType == null || primaryKeyType == void.class) {
            primaryKeyType = Long.class;
        }
        if (primaryKeyType == Long.class || primaryKeyType == long.class) {
            try {
                return Long.parseLong(trimmed);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("id 格式错误，应为数字: " + trimmed, e);
            }
        }
        if (primaryKeyType == String.class) {
            return trimmed;
        }
        if (primaryKeyType == UUID.class) {
            try {
                return UUID.fromString(trimmed);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("id 格式错误，应为 UUID: " + trimmed, e);
            }
        }
        if (primaryKeyType == Integer.class || primaryKeyType == int.class) {
            try {
                return Integer.parseInt(trimmed);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("id 格式错误，应为整数: " + trimmed, e);
            }
        }
        throw new IllegalArgumentException("不支持的主键类型: " + primaryKeyType.getName());
    }

    /**
     * 将请求体中的 id 值（可能为 Number、String 等）按主键类型转为 Object。
     */
    public static Object parseIdFromObject(Object idObj, Class<?> primaryKeyType) {
        if (idObj == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        if (primaryKeyType == null || primaryKeyType == void.class) {
            primaryKeyType = Long.class;
        }
        if (idObj instanceof String) {
            return parseId((String) idObj, primaryKeyType);
        }
        if (primaryKeyType == Long.class || primaryKeyType == long.class) {
            if (idObj instanceof Number n) {
                return n.longValue();
            }
            return parseId(idObj.toString(), primaryKeyType);
        }
        if (primaryKeyType == String.class) {
            return idObj.toString();
        }
        if (primaryKeyType == UUID.class) {
            if (idObj instanceof UUID) {
                return idObj;
            }
            return parseId(idObj.toString(), primaryKeyType);
        }
        if (primaryKeyType == Integer.class || primaryKeyType == int.class) {
            if (idObj instanceof Number n) {
                return n.intValue();
            }
            return parseId(idObj.toString(), primaryKeyType);
        }
        throw new IllegalArgumentException("不支持的主键类型: " + primaryKeyType.getName());
    }

    /**
     * 批量解析：将 List 中每项按 primaryKeyType 转为 Object，得到 List&lt;Object&gt;。
     */
    public static List<Object> parseIds(List<?> idList, Class<?> primaryKeyType) {
        if (idList == null || idList.isEmpty()) {
            return List.of();
        }
        List<Object> result = new ArrayList<>(idList.size());
        for (Object item : idList) {
            result.add(parseIdFromObject(item, primaryKeyType));
        }
        return result;
    }
}
