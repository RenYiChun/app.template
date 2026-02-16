package com.lrenyi.template.platform.support;

import java.util.Collections;
import java.util.List;

/**
 * 搜索/导出请求体。filters、sort 为 null 时视为空列表。
 */
public record SearchRequest(
        List<FilterCondition> filters,
        List<SortOrder> sort,
        int page,
        int size
) {
    public SearchRequest {
        if (filters == null) {
            filters = Collections.emptyList();
        }
        if (sort == null) {
            sort = Collections.emptyList();
        }
    }

    public static SearchRequest empty() {
        return new SearchRequest(Collections.emptyList(), Collections.emptyList(), 0, 20);
    }

    /**
     * 用于 export 的默认 size。
     */
    public static SearchRequest emptyForExport() {
        return new SearchRequest(Collections.emptyList(), Collections.emptyList(), 0, 10000);
    }
}
