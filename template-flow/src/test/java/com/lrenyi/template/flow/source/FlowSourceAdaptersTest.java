package com.lrenyi.template.flow.source;

import com.lrenyi.template.flow.api.FlowSource;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowSourceAdaptersTest {

    @Test
    void emptyProvider_hasNextSubSource_returnsFalse() throws Exception {
        FlowSourceProvider<String> p = FlowSourceAdapters.emptyProvider();
        assertFalse(p.hasNextSubSource());
    }

    @Test
    void emptyProvider_nextSubSource_throws() {
        FlowSourceProvider<String> p = FlowSourceAdapters.emptyProvider();
        assertThrows(NoSuchElementException.class, p::nextSubSource);
    }

    @Test
    void emptyProvider_close_noOp() {
        FlowSourceProvider<String> p = FlowSourceAdapters.emptyProvider();
        assertDoesNotThrow(p::close);
    }

    @Test
    void fromIterator_hasNext_next_close() throws Exception {
        FlowSource<String> src = FlowSourceAdapters.fromIterator(List.of("a", "b").iterator(), null);
        assertTrue(src.hasNext());
        assertEquals("a", src.next());
        assertTrue(src.hasNext());
        assertEquals("b", src.next());
        assertFalse(src.hasNext());
        src.close();
    }

    @Test
    void fromIterator_withOnClose_invokesOnClose() throws Exception {
        boolean[] closed = { false };
        FlowSource<String> src = FlowSourceAdapters.fromIterator(List.of("x").iterator(), () -> closed[0] = true);
        assertTrue(src.hasNext());
        assertEquals("x", src.next());
        src.close();
        assertTrue(closed[0]);
    }

    @Test
    void fromFlowSources_nullOrEmpty_throws() {
        assertThrows(IllegalArgumentException.class, () -> FlowSourceAdapters.fromFlowSources(null));
        assertThrows(IllegalArgumentException.class, () -> FlowSourceAdapters.fromFlowSources(List.of()));
    }

    @Test
    void fromFlowSources_hasNextSubSource_nextSubSource_close() throws Exception {
        FlowSource<String> s1 = FlowSourceAdapters.fromIterator(List.of("a").iterator(), null);
        FlowSource<String> s2 = FlowSourceAdapters.fromIterator(List.of("b").iterator(), null);
        FlowSourceProvider<String> p = FlowSourceAdapters.fromFlowSources(List.of(s1, s2));
        assertTrue(p.hasNextSubSource());
        FlowSource<String> sub1 = p.nextSubSource();
        assertNotNull(sub1);
        assertTrue(sub1.hasNext());
        assertEquals("a", sub1.next());
        assertTrue(p.hasNextSubSource());
        FlowSource<String> sub2 = p.nextSubSource();
        assertEquals("b", sub2.next());
        assertFalse(p.hasNextSubSource());
        assertThrows(NoSuchElementException.class, p::nextSubSource);
        p.close();
    }

    @Test
    void singleSourceProvider_hasNext_nextSubSource_returnsSingle() throws Exception {
        FlowSource<String> single = FlowSourceAdapters.fromIterator(List.of("x").iterator(), null);
        FlowSourceProvider<String> p = FlowSourceAdapters.singleSourceProvider(single);
        assertTrue(p.hasNextSubSource());
        assertSame(single, p.nextSubSource());
        assertFalse(p.hasNextSubSource());
        p.close();
    }

    @Test
    void fromFlowSources_closeWhenOneSourceThrows_doesNotPropagate() {
        FlowSource<String> ok = FlowSourceAdapters.fromIterator(List.of("a").iterator(), null);
        FlowSource<String> throwOnClose = new ThrowingOnCloseFlowSource<>();
        FlowSourceProvider<String> p = FlowSourceAdapters.fromFlowSources(List.of(ok, throwOnClose));
        assertDoesNotThrow(p::close);
    }

    private static class ThrowingOnCloseFlowSource<T> implements FlowSource<T> {
        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public T next() {
            throw new NoSuchElementException();
        }

        @Override
        public void close() {
            throw new RuntimeException("close failed");
        }
    }
}
