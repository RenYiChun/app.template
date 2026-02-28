package com.lrenyi.template.flow.sources.paged;

/**
 * 分页拉取器：根据页码/游标拉取一页数据。
 * 供 {@link PagedFlowSource} 使用；若底层为阻塞 I/O（如 HTTP），可抛 {@link InterruptedException}。
 *
 * @param <T> 单条数据类型
 */
@FunctionalInterface
public interface PageFetcher<T> {
    
    /**
     * 拉取一页。
     *
     * @param pageToken 页码或游标，首次调用时为 null
     * @return 当前页结果，{@link PageResult#nextPageToken()} 为 null 表示没有下一页
     * @throws InterruptedException 若拉取过程中线程被中断
     */
    PageResult<T> fetch(Object pageToken) throws InterruptedException;
}
