package com.lrenyi.template.flow.sources.paged;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagedFlowSourceTest {
    
    @Test
    void hasNextNextCloseSinglePage() throws Exception {
        PageFetcher<String> fetcher = token -> PageResult.of(List.of("a", "b"));
        PagedFlowSource<String> source = new PagedFlowSource<>(fetcher);
        
        assertTrue(source.hasNext());
        assertEquals("a", source.next());
        assertTrue(source.hasNext());
        assertEquals("b", source.next());
        assertFalse(source.hasNext());
        
        source.close();
        assertFalse(source.hasNext());
        assertThrows(NoSuchElementException.class, source::next);
    }
    
    @Test
    void hasNextMultiplePages() throws Exception {
        PageFetcher<String> fetcher = token -> {
            if (token == null) {
                return PageResult.of(List.of("a"), "t1");
            }
            if ("t1".equals(token)) {
                return PageResult.of(List.of("b", "c"), null);
            }
            return PageResult.of(List.of());
        };
        PagedFlowSource<String> source = new PagedFlowSource<>(fetcher);
        
        assertTrue(source.hasNext());
        assertEquals("a", source.next());
        assertTrue(source.hasNext());
        assertEquals("b", source.next());
        assertEquals("c", source.next());
        assertFalse(source.hasNext());
    }
    
    @Test
    void closeInvokesOnClose() {
        AtomicBoolean closed = new AtomicBoolean(false);
        PageFetcher<String> fetcher = token -> PageResult.of(List.of());
        PagedFlowSource<String> source = new PagedFlowSource<>(fetcher, () -> closed.set(true));
        
        source.close();
        assertTrue(closed.get());
    }
    
    @Test
    void closeIdempotent() {
        AtomicBoolean closed = new AtomicBoolean(false);
        PageFetcher<String> fetcher = token -> PageResult.of(List.of());
        PagedFlowSource<String> source = new PagedFlowSource<>(fetcher, () -> closed.set(true));
        
        source.close();
        source.close();
        assertTrue(closed.get());
    }
    
    @Test
    void nextWithoutHasNextThrows() throws Exception {
        PagedFlowSource<String> source = new PagedFlowSource<>(token -> PageResult.of(List.of("x")));
        assertTrue(source.hasNext());
        assertEquals("x", source.next());
        assertFalse(source.hasNext());
        assertThrows(NoSuchElementException.class, source::next);
    }
    
    /** 单参构造：onClose 为 null，close 不抛 */
    @Test
    void constructorSingleArgOnCloseNullCloseSafe() throws Exception {
        PagedFlowSource<String> source = new PagedFlowSource<>(token -> PageResult.of(List.of()));
        assertFalse(source.hasNext());
        source.close();
        source.close();
    }
    
    /** 首页为空且无下一页时 hasNext 返回 false */
    @Test
    void hasNextEmptyFirstPageReturnsFalse() throws Exception {
        PageFetcher<String> fetcher = token -> PageResult.of(List.of());
        PagedFlowSource<String> source = new PagedFlowSource<>(fetcher);
        assertFalse(source.hasNext());
    }
    
    /** next 在已 close 后调用抛 NoSuchElementException */
    @Test
    void nextAfterCloseThrows() {
        PagedFlowSource<String> source = new PagedFlowSource<>(token -> PageResult.of(List.of("a")));
        source.close();
        assertThrows(NoSuchElementException.class, source::next);
    }

    @Test
    void constructorNullFetcherThrows() {
        assertThrows(IllegalArgumentException.class, () -> new PagedFlowSource<String>(null));
    }

    @Test
    void closeBestEffortSwallowsCallbackFailure() {
        AtomicInteger calls = new AtomicInteger();
        PagedFlowSource<String> source = new PagedFlowSource<>(token -> PageResult.of(List.of()),
                                                               () -> {
                                                                   calls.incrementAndGet();
                                                                   throw new RuntimeException("close failed");
                                                               });

        assertDoesNotThrow(source::close);
        assertEquals(1, calls.get());
        assertDoesNotThrow(source::close);
        assertEquals(1, calls.get());
    }
    
    /** 第一页有数据带 token，第二页为空且无 token */
    @Test
    void hasNextSecondPageEmptyReturnsFalse() throws Exception {
        PageFetcher<String> fetcher = token -> {
            if (token == null) {
                return PageResult.of(List.of("only"), "t1");
            }
            return PageResult.of(List.of(), null);
        };
        PagedFlowSource<String> source = new PagedFlowSource<>(fetcher);
        assertTrue(source.hasNext());
        assertEquals("only", source.next());
        assertFalse(source.hasNext());
    }
    
    /** hasNext 被中断时抛 InterruptedException */
    @Test
    void hasNextInterruptedThrowsInterruptedException() throws Exception {
        PagedFlowSource<String> source = new PagedFlowSource<>(token -> PageResult.of(List.of()));
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                Thread.currentThread().interrupt();
                source.hasNext();
            } catch (Throwable e) {
                thrown.set(e);
            }
        });
        t.start();
        t.join(3000);
        assertTrue(thrown.get() instanceof InterruptedException, "expected InterruptedException, got " + thrown.get());
    }
}
