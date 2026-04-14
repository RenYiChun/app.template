package com.lrenyi.template.flow.config;

import com.lrenyi.template.flow.model.FlowStorageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlowStorageTypeTest {
    
    @Test
    void fromLocalBoundedReturnsLocalBounded() {
        assertEquals(FlowStorageType.LOCAL_BOUNDED, FlowStorageType.from("local_bounded"));
        assertEquals(FlowStorageType.LOCAL_BOUNDED, FlowStorageType.from("LOCAL_BOUNDED"));
    }
    
    @Test
    void fromQueueReturnsQueue() {
        assertEquals(FlowStorageType.QUEUE, FlowStorageType.from("queue"));
        assertEquals(FlowStorageType.QUEUE, FlowStorageType.from("QUEUE"));
    }
    
    @Test
    void fromUnknownReturnsLocalBoundedDefault() {
        assertEquals(FlowStorageType.LOCAL_BOUNDED, FlowStorageType.from("unknown"));
        assertEquals(FlowStorageType.LOCAL_BOUNDED, FlowStorageType.from(""));
        assertEquals(FlowStorageType.LOCAL_BOUNDED, FlowStorageType.from(null));
    }
}
