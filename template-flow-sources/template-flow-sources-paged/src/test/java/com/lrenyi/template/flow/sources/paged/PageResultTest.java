package com.lrenyi.template.flow.sources.paged;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PageResultTest {
    
    @Test
    void ofItemsOnlyNullItemsUsesEmptyList() {
        PageResult<String> r = PageResult.of(null);
        assertEquals(List.of(), r.items());
        assertNull(r.nextPageToken());
    }
    
    @Test
    void ofItemsOnlyNonNullItems() {
        PageResult<String> r = PageResult.of(List.of("a", "b"));
        assertEquals(List.of("a", "b"), r.items());
        assertNull(r.nextPageToken());
    }
    
    @Test
    void ofItemsAndTokenNullItemsUsesEmptyList() {
        PageResult<String> r = PageResult.of(null, "token2");
        assertEquals(List.of(), r.items());
        assertEquals("token2", r.nextPageToken());
    }
    
    @Test
    void ofItemsAndTokenNonNullItems() {
        PageResult<Integer> r = PageResult.of(List.of(1, 2), 3);
        assertEquals(List.of(1, 2), r.items());
        assertEquals(3, r.nextPageToken());
    }
}
