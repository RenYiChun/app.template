package com.lrenyi.template.flow.config;

import com.lrenyi.template.flow.model.FlowStorageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlowStorageTypeTest {
    
    @Test
    void fromCaffeineReturnsCaffeine() {
        assertEquals(FlowStorageType.CAFFEINE, FlowStorageType.from("caffeine"));
        assertEquals(FlowStorageType.CAFFEINE, FlowStorageType.from("CAFFEINE"));
    }
    
    @Test
    void fromQueueReturnsQueue() {
        assertEquals(FlowStorageType.QUEUE, FlowStorageType.from("queue"));
        assertEquals(FlowStorageType.QUEUE, FlowStorageType.from("QUEUE"));
    }
    
    @Test
    void fromUnknownReturnsCaffeineDefault() {
        assertEquals(FlowStorageType.CAFFEINE, FlowStorageType.from("unknown"));
        assertEquals(FlowStorageType.CAFFEINE, FlowStorageType.from(""));
        assertEquals(FlowStorageType.CAFFEINE, FlowStorageType.from(null));
    }
}
