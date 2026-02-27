package com.lrenyi.template.flow.sources.paged;

import java.util.Iterator;
import java.util.NoSuchElementException;
import com.lrenyi.template.flow.api.FlowSource;

/**
 * 单子流分页数据源：包装 {@link PageFetcher}，按页拉取并顺序产出 T。
 * 适用于 HTTP/RPC 分页 API：首次 {@link #hasNext()} 调用 {@link PageFetcher#fetch(Object) fetch(null)}，
 * 当前页消费完后用上一页返回的 nextPageToken 拉取下一页，直到无下一页。
 * <p>
 * 阻塞与中断：{@link #hasNext()} 内拉取新页时若底层阻塞可抛 {@link InterruptedException}。
 *
 * @param <T> 业务产出类型
 */
public final class PagedFlowSource<T> implements FlowSource<T> {

    private final PageFetcher<T> fetcher;
    private final Runnable onClose;

    private Iterator<T> currentPage;
    private Object nextPageToken;
    private boolean closed;
    /** 首次拉取用 null，之后用 nextPageToken */
    private boolean firstFetch = true;

    /**
     * @param fetcher 分页拉取器，非 null
     */
    public PagedFlowSource(PageFetcher<T> fetcher) {
        this(fetcher, null);
    }

    /**
     * @param fetcher  分页拉取器，非 null
     * @param onClose  关闭时回调，可为 null
     */
    public PagedFlowSource(PageFetcher<T> fetcher, Runnable onClose) {
        this.fetcher = fetcher;
        this.onClose = onClose;
    }

    @Override
    public boolean hasNext() throws InterruptedException {
        if (closed) {
            return false;
        }
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (currentPage != null && currentPage.hasNext()) {
            return true;
        }
        fetchNextPage();
        return currentPage != null && currentPage.hasNext();
    }

    private void fetchNextPage() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (firstFetch) {
            firstFetch = false;
            PageResult<T> result = fetcher.fetch(null);
            currentPage = result.items().iterator();
            nextPageToken = result.nextPageToken();
            return;
        }
        if (nextPageToken == null) {
            currentPage = null;
            return;
        }
        PageResult<T> result = fetcher.fetch(nextPageToken);
        currentPage = result.items().iterator();
        nextPageToken = result.nextPageToken();
    }

    @Override
    public T next() {
        if (closed) {
            throw new NoSuchElementException();
        }
        if (currentPage == null || !currentPage.hasNext()) {
            throw new NoSuchElementException();
        }
        return currentPage.next();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        currentPage = null;
        nextPageToken = null;
        if (onClose != null) {
            onClose.run();
        }
    }
}
