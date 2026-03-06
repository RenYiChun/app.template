package com.lrenyi.template.flow.storage;

import java.util.ArrayList;
import java.util.List;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.context.FlowEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FlowSlot 单元测试
 */
class FlowSlotTest {
    private FlowSlot<String> slot;
    
    @BeforeEach
    void setUp() {
        slot = new FlowSlot<>(16, TemplateConfigProperties.Flow.MultiValueOverflowPolicy.DROP_OLDEST);
    }
    
    @Test
    void offerLastAppendsToDeque() {
        FlowEntry<String> a = new FlowEntry<>("a", "job-1");
        FlowEntry<String> b = new FlowEntry<>("b", "job-1");
        slot.append(a);
        slot.append(b);
        
        FlowEntry<String> c = new FlowEntry<>("c", "job-1");
        slot.offerLast(c);
        
        assertEquals(3, slot.size());
        assertEquals("a", slot.poll().orElseThrow().getData());
        assertEquals("b", slot.poll().orElseThrow().getData());
        assertEquals("c", slot.poll().orElseThrow().getData());
    }
    
    @Test
    void forEachEntryIteratesAll() {
        FlowEntry<String> a = new FlowEntry<>("a", "job-1");
        FlowEntry<String> b = new FlowEntry<>("b", "job-1");
        slot.append(a);
        slot.append(b);
        
        List<String> collected = new ArrayList<>();
        slot.forEachEntry(e -> collected.add(e.getData()));
        
        assertEquals(2, collected.size());
        assertTrue(collected.contains("a"));
        assertTrue(collected.contains("b"));
    }
}
