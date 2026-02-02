package com.lrenyi.template.core.flow.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlowStorageTypeTest {

    @Test
    void from_caffeine_returnsCaffeine() {
        assertEquals(FlowStorageType.CAFFEINE, FlowStorageType.from("caffeine"));
        assertEquals(FlowStorageType.CAFFEINE, FlowStorageType.from("CAFFEINE"));
    }

    @Test
    void from_queue_returnsQueue() {
        assertEquals(FlowStorageType.QUEUE, FlowStorageType.from("queue"));
        assertEquals(FlowStorageType.QUEUE, FlowStorageType.from("QUEUE"));
    }

    @Test
    void from_unknown_returnsCaffeineDefault() {
        assertEquals(FlowStorageType.CAFFEINE, FlowStorageType.from("unknown"));
        assertEquals(FlowStorageType.CAFFEINE, FlowStorageType.from(""));
        assertEquals(FlowStorageType.CAFFEINE, FlowStorageType.from(null));
    }
}
