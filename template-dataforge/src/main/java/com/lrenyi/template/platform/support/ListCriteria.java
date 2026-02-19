package com.lrenyi.template.platform.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import com.lrenyi.template.platform.meta.EntityMeta;
import com.lrenyi.template.platform.meta.FieldMeta;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service 层使用的查询条件，由 SearchRequest 校验转换而来。
 */
@Getter
public final class ListCriteria {

    private static final Logger log = LoggerFactory.getLogger(ListCriteria.class);

    private final List<FilterCondition> filters;
    private final List<SortOrder> sortOrders;

    private ListCriteria(List<FilterCondition> filters, List<SortOrder> sortOrders) {
        this.filters = filters != null ? List.copyOf(filters) : List.of();
        this.sortOrders = sortOrders != null ? List.copyOf(sortOrders) : List.of();
    }
    
    public static ListCriteria empty() {
        return new ListCriteria(List.of(), List.of());
    }

    /**
     * 从 SearchRequest 构建 ListCriteria，校验 field 在 allowedFields 内，
     * 按 FieldMeta.type 转换 value，非法条件忽略并记录 debug 日志。
     */
    public static ListCriteria from(SearchRequest req, EntityMeta entityMeta) {
        if (entityMeta == null || entityMeta.getFields() == null) {
            return empty();
        }
        if (req == null || req.filters() == null || req.filters().isEmpty()) {
            List<SortOrder> sortOrders = validateSort(req, entityMeta);
            return new ListCriteria(List.of(), sortOrders);
        }
        Set<String> allowedFields = entityMeta.getFields().stream()
                .map(FieldMeta::getName)
                .collect(Collectors.toSet());
        Map<String, FieldMeta> fieldMetaByName = entityMeta.getFields().stream()
                .collect(Collectors.toMap(FieldMeta::getName, fm -> fm, (a, b) -> a));
        List<FilterCondition> valid = new ArrayList<>();
        for (FilterCondition fc : req.filters()) {
            if (fc == null || fc.field() == null || fc.field().isBlank()) {
                continue;
            }
            if (!allowedFields.contains(fc.field())) {
                log.debug("Filter field '{}' not in allowedFields, skip", fc.field());
                continue;
            }
            if (fc.op() == null) {
                log.debug("Filter op is null for field '{}', skip", fc.field());
                continue;
            }
            FieldMeta fm = fieldMetaByName.get(fc.field());
            Object converted = convertValue(fc.value(), fc.op(), fm);
            if (converted == null && fc.value() != null && fc.op() != Op.IN) {
                log.debug("Filter value conversion failed for field '{}', skip", fc.field());
                continue;
            }
            valid.add(new FilterCondition(fc.field(), fc.op(), converted));
        }
        List<SortOrder> sortOrders = validateSort(req, entityMeta);
        return new ListCriteria(valid, sortOrders);
    }

    private static List<SortOrder> validateSort(SearchRequest req, EntityMeta entityMeta) {
        if (req == null || req.sort() == null || req.sort().isEmpty()) {
            return List.of();
        }
        if (entityMeta == null || entityMeta.getFields() == null) {
            return List.of();
        }
        Set<String> allowedFields = entityMeta.getFields().stream()
                .map(FieldMeta::getName)
                .collect(Collectors.toSet());
        List<SortOrder> valid = new ArrayList<>();
        for (SortOrder so : req.sort()) {
            if (so == null || so.field() == null || so.field().isBlank()) {
                continue;
            }
            if (!allowedFields.contains(so.field())) {
                log.debug("Sort field '{}' not in allowedFields, skip", so.field());
                continue;
            }
            String dir = (so.dir() != null && so.dir().equalsIgnoreCase("desc")) ? "desc" : "asc";
            valid.add(new SortOrder(so.field(), dir));
        }
        return valid;
    }

    private static Object convertValue(Object raw, Op op, FieldMeta fm) {
        if (raw == null) {
            return null;
        }
        String type = fm != null ? fm.getType() : null;
        if (type == null) {
            return raw;
        }
        try {
            if (op == Op.IN) {
                if (raw instanceof List<?> list) {
                    return list.stream().map(v -> convertSingle(v, type)).filter(Objects::nonNull)
                            .toList();
                }
                return List.of(convertSingle(raw, type));
            }
            return convertSingle(raw, type);
        } catch (Exception e) {
            log.debug("Value conversion failed: {}", e.getMessage());
            return null;
        }
    }

    private static Object convertSingle(Object raw, String type) {
        if (raw == null) {
            return null;
        }
        if (type == null) {
            return raw;
        }
        return switch (type) {
            case "String" -> raw.toString();
            case "Integer", "int" -> raw instanceof Number n ? n.intValue() : Integer.parseInt(raw.toString());
            case "Long", "long" -> raw instanceof Number n ? n.longValue() : Long.parseLong(raw.toString());
            case "Boolean", "boolean" -> raw instanceof Boolean b ? b : "true".equalsIgnoreCase(raw.toString());
            case "Double", "double" -> raw instanceof Number n ? n.doubleValue() : Double.parseDouble(raw.toString());
            case "Float", "float" -> raw instanceof Number n ? n.floatValue() : Float.parseFloat(raw.toString());
            case "LocalDate" -> raw instanceof String s
                    ? LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE)
                    : raw;
            case "LocalDateTime" -> raw instanceof String s
                    ? LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    : raw;
            default -> raw;
        };
    }
}
