package com.lrenyi.template.dataforge.jpa.support;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.lrenyi.template.dataforge.support.FilterCondition;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

/**
 * 将 FilterCondition 列表转换为 JPA Specification，用于 JpaSpecificationExecutor。
 */
public final class FilterConditionSpecification {
    
    private FilterConditionSpecification() {
    }
    
    /**
     * 从 FilterCondition 列表构建 Specification，字段名需在 allowedFields 内。
     */
    public static <T> Specification<T> from(List<FilterCondition> filters, Set<String> allowedFields) {
        if (filters == null || filters.isEmpty()) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> {
            List<Predicate> predicates = filters.stream()
                                                .filter(fc -> fc != null && fc.op() != null && fc.field() != null
                                                        && !fc.field().isBlank())
                                                .filter(fc -> allowedFields.contains(fc.field()))
                                                .map(fc -> toPredicate(root, cb, fc))
                                                .filter(Objects::nonNull)
                                                .toList();
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
    }
    
    @SuppressWarnings("unchecked")
    private static <T> Predicate toPredicate(Root<T> root, CriteriaBuilder cb, FilterCondition fc) {
        Path<Object> path = root.get(fc.field());
        Object value = fc.value();
        return switch (fc.op()) {
            case EQ -> cb.equal(path, value);
            case NE -> cb.notEqual(path, value);
            case LIKE -> {
                String pattern = value instanceof String s ? "%" + s.toLowerCase() + "%" :
                        "%" + (value != null ? value : "") + "%";
                yield cb.like(cb.lower(path.as(String.class)), pattern);
            }
            case GT ->
                    value instanceof Comparable<?> c ? cb.greaterThan(path.as(Comparable.class), (Comparable) c) : null;
            case GTE -> value instanceof Comparable<?> c ?
                    cb.greaterThanOrEqualTo(path.as(Comparable.class), (Comparable) c) : null;
            case LT -> value instanceof Comparable<?> c ? cb.lessThan(path.as(Comparable.class), (Comparable) c) : null;
            case LTE ->
                    value instanceof Comparable<?> c ? cb.lessThanOrEqualTo(path.as(Comparable.class), (Comparable) c) :
                            null;
            case IN -> path.in(value instanceof List<?> l ? l : List.of(value));
        };
    }
}
