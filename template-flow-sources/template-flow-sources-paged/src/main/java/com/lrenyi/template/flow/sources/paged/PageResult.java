package com.lrenyi.template.flow.sources.paged;

import java.util.Collections;
import java.util.List;

/**
 * 单页结果：当前页数据与下一页令牌。
 * 用于 {@link PageFetcher} 与 {@link PagedFlowSource}。
 *
 * @param <T> 单条数据类型
 * @param items        当前页数据，非 null（可为空列表）
 * @param nextPageToken 下一页令牌，null 表示没有下一页
 */
public record PageResult<T>(List<T> items, Object nextPageToken) {
    
    /**
     * 构造无下一页的结果。
     */
    public static <T> PageResult<T> of(List<T> items) {
        return new PageResult<>(items == null ? Collections.emptyList() : items, null);
    }
    
    /**
     * 构造含下一页令牌的结果。
     */
    public static <T> PageResult<T> of(List<T> items, Object nextPageToken) {
        return new PageResult<>(items == null ? Collections.emptyList() : items, nextPageToken);
    }
}
