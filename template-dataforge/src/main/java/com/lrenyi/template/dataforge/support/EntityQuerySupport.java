package com.lrenyi.template.dataforge.support;

import java.util.List;
import com.lrenyi.template.dataforge.config.DataforgeProperties;
import com.lrenyi.template.dataforge.meta.EntityMeta;
import com.lrenyi.template.dataforge.permission.DataPermissionApplicator;
import com.lrenyi.template.dataforge.service.EntityCrudService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 统一封装查询请求的分页和数据权限拼装，减少控制器中的重复代码。
 */
public class EntityQuerySupport {

    private final EntityCrudService crudService;
    private final DataforgeProperties properties;
    private final ObjectProvider<DataPermissionApplicator> dataPermissionApplicatorProvider;

    public EntityQuerySupport(EntityCrudService crudService,
            DataforgeProperties properties,
            ObjectProvider<DataPermissionApplicator> dataPermissionApplicatorProvider) {
        this.crudService = crudService;
        this.properties = properties;
        this.dataPermissionApplicatorProvider = dataPermissionApplicatorProvider;
    }

    public QueryExecution prepareSearch(EntityMeta meta, SearchRequest request) {
        SearchRequest safeRequest = request != null ? request : SearchRequest.empty();
        int safeSize = Math.clamp(safeRequest.size(), 1, properties.getMaxPageSize());
        Pageable pageable = buildPageable(safeRequest.page(), safeSize, safeRequest.sort());
        ListCriteria criteria = withDataPermission(meta, ListCriteria.from(safeRequest, meta));
        return new QueryExecution(pageable, criteria);
    }

    public QueryExecution prepareExport(EntityMeta meta, SearchRequest request) {
        SearchRequest safeRequest = request != null ? request : SearchRequest.emptyForExport();
        int safeSize = Math.clamp(safeRequest.size(), 1, properties.getMaxExportSize());
        Pageable pageable = buildPageable(safeRequest.page(), safeSize, safeRequest.sort());
        ListCriteria criteria = withDataPermission(meta, ListCriteria.from(safeRequest, meta));
        return new QueryExecution(pageable, criteria);
    }

    public Page<?> list(EntityMeta meta, QueryExecution execution) {
        return crudService.list(meta, execution.pageable(), execution.criteria());
    }

    public ListCriteria withDataPermission(EntityMeta meta, ListCriteria criteria) {
        List<FilterCondition> filters = criteria != null ? criteria.getFilters() : List.of();
        List<FilterCondition> merged = mergeDataPermissionFilters(meta, filters);
        List<SortOrder> sortOrders = criteria != null ? criteria.getSortOrders() : List.of();
        return ListCriteria.of(merged, sortOrders);
    }

    public List<FilterCondition> mergeDataPermissionFilters(EntityMeta meta, List<FilterCondition> baseFilters) {
        List<FilterCondition> filters = new java.util.ArrayList<>(baseFilters);
        if (meta.isEnableDataPermission()) {
            DataPermissionApplicator applicator = dataPermissionApplicatorProvider.getIfAvailable();
            if (applicator != null) {
                List<FilterCondition> extra = applicator.getDataPermissionFilters(meta);
                if (extra != null && !extra.isEmpty()) {
                    filters.addAll(extra);
                }
            }
        }
        return filters;
    }

    private static Pageable buildPageable(int page, int size, List<SortOrder> sortOrders) {
        org.springframework.data.domain.Sort sort = org.springframework.data.domain.Sort.unsorted();
        if (sortOrders != null && !sortOrders.isEmpty()) {
            List<org.springframework.data.domain.Sort.Order> orders = new java.util.ArrayList<>();
            for (SortOrder so : sortOrders) {
                if (so != null && so.field() != null && !so.field().isBlank()) {
                    orders.add("desc".equalsIgnoreCase(so.dir()) ? org.springframework.data.domain.Sort.Order.desc(so.field()) :
                            org.springframework.data.domain.Sort.Order.asc(so.field()));
                }
            }
            if (!orders.isEmpty()) {
                sort = org.springframework.data.domain.Sort.by(orders);
            }
        }
        return org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.max(1, size), sort);
    }

    public record QueryExecution(Pageable pageable, ListCriteria criteria) {
    }
}
