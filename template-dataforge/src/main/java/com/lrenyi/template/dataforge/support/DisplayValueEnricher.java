package com.lrenyi.template.dataforge.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.meta.FieldMeta;
import com.lrenyi.template.dataforge.registry.EntityRegistry;
import com.lrenyi.template.dataforge.service.EntityCrudService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

/**
 * 为列表项填充关联字段的 _display 显示值（通过批量查询关联实体，避免 N+1）。
 * 将列表项转为 Map 后写入 {@code fieldName + "_display"}，返回 List&lt;Map&lt;String, Object&gt;&gt;。
 */
public final class DisplayValueEnricher {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private DisplayValueEnricher() {}

    /**
     * 对列表项按 meta 中的 foreignKey 字段批量解析显示值并写入 _display，返回 Map 列表。
     * 若 meta 无 foreignKey、list 为空或 enricher 依赖缺失，则仅将 item 转为 Map 返回。
     */
    public static List<Map<String, Object>> enrich(EntityMeta meta, List<?> list,
            EntityRegistry entityRegistry, EntityCrudService crudService, ObjectMapper objectMapper) {
        if (list == null || list.isEmpty() || objectMapper == null) {
            return list == null ? List.of() : list.stream()
                    .map(item -> toMap(item, objectMapper))
                    .toList();
        }
        List<Map<String, Object>> maps = list.stream()
                .map(item -> toMap(item, objectMapper))
                .toList();
        if (entityRegistry == null || crudService == null || meta == null || meta.getFields() == null) {
            return maps;
        }
        for (FieldMeta field : meta.getFields()) {
            if (!field.isForeignKey()) {
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
            Set<Object> ids = maps.stream()
                    .map(m -> m.get(field.getName()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (ids.isEmpty()) {
                continue;
            }
            String displayField = StringUtils.hasText(field.getDisplayField()) ? field.getDisplayField() : "name";
            Map<Object, Object> idToLabel = new LinkedHashMap<>();
            Map<Object, Boolean> idToDeleted = new LinkedHashMap<>();
            for (Object id : ids) {
                Object ref = crudService.get(refMeta, id);
                if (ref != null) {
                    Object label = refMeta.getAccessor().get(ref, displayField);
                    idToLabel.put(id, label != null ? label : id);
                    idToDeleted.put(id, isRefSoftDeleted(refMeta, ref));
                }
            }
            String displayKey = field.getName() + "_display";
            String statusKey = field.getName() + "_status";
            for (Map<String, Object> map : maps) {
                Object id = map.get(field.getName());
                if (id != null && idToLabel.containsKey(id)) {
                    map.put(displayKey, idToLabel.get(id));
                    if (Boolean.TRUE.equals(idToDeleted.get(id))) {
                        map.put(statusKey, "deleted");
                    }
                }
            }
        }
        return maps;
    }

    private static boolean isRefSoftDeleted(EntityMeta refMeta, Object ref) {
        if (ref == null || refMeta.getAccessor() == null || !refMeta.isSoftDelete()) {
            return false;
        }
        String timeField = refMeta.getDeleteTimeField();
        if (StringUtils.hasText(timeField)) {
            Object t = refMeta.getAccessor().get(ref, timeField);
            if (t != null) {
                return true;
            }
        }
        String flagField = refMeta.getDeleteFlagField();
        if (StringUtils.hasText(flagField)) {
            Object f = refMeta.getAccessor().get(ref, flagField);
            if (Boolean.TRUE.equals(f) || (f instanceof String s && "true".equalsIgnoreCase(s))) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> toMap(Object item, ObjectMapper objectMapper) {
        if (item instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) item;
            return new LinkedHashMap<>(cast);
        }
        return objectMapper.convertValue(item, MAP_TYPE);
    }
}
