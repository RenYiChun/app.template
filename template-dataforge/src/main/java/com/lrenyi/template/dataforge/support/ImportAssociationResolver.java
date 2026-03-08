package com.lrenyi.template.dataforge.support;

import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.meta.FieldMeta;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import com.lrenyi.template.dataforge.service.EntityCrudService;
import java.util.LinkedHashMap;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

/**
 * 导入时关联字段预加载：按 displayField 列出被引用实体，构建 displayValue -> id 映射。
 * 应用层在解析 Excel 行时用 {@link #resolveId} 将单元格显示值解析为关联 ID，找不到时抛出带行号/字段/值的异常。
 */
public final class ImportAssociationResolver {

    private ImportAssociationResolver() {}

    /**
     * 为实体的所有启用导入的 foreignKey 字段预加载 displayValue -> id 映射。
     * 重名时保留第一个（(existing, replacement) -> existing）。
     *
     * @param meta 当前实体元数据
     * @param entityRegistry 实体注册表
     * @param crudService CRUD 服务（list 会应用数据权限若已配置）
     * @return key 为字段名，value 为 displayValue -> id
     */
    public static Map<String, Map<Object, Object>> preloadDisplayToId(EntityMeta meta,
            EntityRegistry entityRegistry, EntityCrudService crudService) {
        Map<String, Map<Object, Object>> out = new LinkedHashMap<>();
        if (meta == null || meta.getFields() == null || entityRegistry == null || crudService == null) {
            return out;
        }
        for (FieldMeta field : meta.getFields()) {
            if (!field.isForeignKey() || !field.isImportEnabled()) {
                continue;
            }
            String refEntity = field.getReferencedEntity();
            if (!StringUtils.hasText(refEntity)) {
                continue;
            }
            EntityMeta refMeta = entityRegistry.getByEntityName(refEntity.trim());
            if (refMeta == null || refMeta.getAccessor() == null) {
                continue;
            }
            String displayField = StringUtils.hasText(field.getDisplayField()) ? field.getDisplayField() : "name";
            List<?> list = crudService.list(refMeta, Pageable.unpaged(), ListCriteria.empty()).getContent();
            Map<Object, Object> displayToId = new LinkedHashMap<>();
            for (Object entity : list) {
                Object id = refMeta.getAccessor().get(entity, "id");
                Object displayVal = refMeta.getAccessor().get(entity, displayField);
                if (id != null && displayVal != null) {
                    displayToId.putIfAbsent(normalizeDisplay(displayVal), id);
                }
            }
            out.put(field.getName(), displayToId);
        }
        return out;
    }

    /**
     * 将单元格显示值解析为关联 ID。若 displayToId 中无该值，抛出 DataforgeHttpException（IMPORT_ASSOCIATION_NOT_FOUND）。
     *
     * @param fieldName 字段名
     * @param displayValue 单元格值（显示值）
     * @param displayToId 预加载的 displayValue -> id 映射
     * @param rowIndex 行号（1-based，用于错误提示）
     * @param fieldLabel 字段标签（用于错误提示）
     * @return 关联 ID
     */
    public static Object resolveId(String fieldName, Object displayValue, Map<Object, Object> displayToId,
            int rowIndex, String fieldLabel) {
        if (displayValue == null || (displayValue instanceof String s && !StringUtils.hasText(s))) {
            return null;
        }
        Object key = normalizeDisplay(displayValue);
        Object id = displayToId != null ? displayToId.get(key) : null;
        if (id == null) {
            throw new DataforgeHttpException(HttpStatus.BAD_REQUEST.value(),
                    DataforgeErrorCodes.IMPORT_ASSOCIATION_NOT_FOUND,
                    String.format("第 %d 行：%s「%s」不存在或无权限", rowIndex, StringUtils.hasText(fieldLabel) ? fieldLabel : fieldName, displayValue));
        }
        return id;
    }

    private static Object normalizeDisplay(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
