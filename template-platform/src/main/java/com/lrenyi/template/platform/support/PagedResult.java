package com.lrenyi.template.platform.support;

import java.util.List;

/**
 * 分页结果 DTO，用于替代直接序列化 PageImpl，避免 Spring Data 的序列化警告。
 * JSON 结构稳定：content、totalElements、totalPages、number、size。
 */
public final class PagedResult<T> {

    private final List<T> content;
    private final long totalElements;
    private final int totalPages;
    private final int number;
    private final int size;

    public PagedResult(List<T> content, long totalElements, int totalPages, int number, int size) {
        this.content = content != null ? content : List.of();
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.number = number;
        this.size = size;
    }

    public List<T> getContent() {
        return content;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public int getNumber() {
        return number;
    }

    public int getSize() {
        return size;
    }

    /**
     * 从 Spring Data Page 构建 PagedResult，使用已转换的 content。
     */
    @SuppressWarnings("unchecked")
    public static <T> PagedResult<T> from(org.springframework.data.domain.Page<?> page, List<?> content) {
        if (page == null) {
            return new PagedResult<>((List<T>) List.of(), 0, 0, 0, 20);
        }
        return new PagedResult<>(
                (List<T>) (content != null ? content : List.of()),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize());
    }
}
