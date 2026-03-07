package com.lrenyi.template.flow.context;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlowProgressSnapshotTest {
    
    private static final String REASON_TIMEOUT = "TIMEOUT";
    
    @Test
    void constructorNullPassiveEgressByReasonUsesEmptyMap() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 10, 5, 3, 2, 1, 2, 1, 3, 0, 0, null);
        assertEquals(0, s.passiveEgressByReason().size());
    }
    
    @Test
    void constructorNonNullPassiveEgressByReasonUnmodifiable() {
        Map<String, Long> map = new java.util.HashMap<>(Map.of(REASON_TIMEOUT, 1L));
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, map);
        assertEquals(1, s.passiveEgressByReason().size());
        Map<String, Long> passiveMap = s.passiveEgressByReason();
        assertThrows(UnsupportedOperationException.class, () -> passiveMap.put("x", 1L));
    }
    
    @Test
    void getCompletionRateTotalExpectedPositive() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 10, 10, 10, 0, 0, 8, 2, 10, 0, 0, null);
        assertEquals(1.0, s.getCompletionRate());
        FlowProgressSnapshot s2 = new FlowProgressSnapshot("j1", 10, 5, 3, 2, 1, 1, 1, 2, 0, 0, null);
        assertEquals(0.2, s2.getCompletionRate());
    }
    
    @Test
    void getCompletionRateTotalExpectedZeroEndTimeSetReturnsOne() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 0, 5, 5, 0, 0, 5, 0, 5, 100, 200, null);
        assertEquals(1.0, s.getCompletionRate());
    }
    
    @Test
    void getCompletionRateTotalExpectedZeroProductionAcquiredZeroReturnsZero() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null);
        assertEquals(0.0, s.getCompletionRate());
    }
    
    @Test
    void getCompletionRateTotalExpectedZeroTerminatedOverAcquired() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 0, 10, 10, 0, 0, 8, 2, 10, 0, 0, null);
        assertEquals(1.0, s.getCompletionRate());
    }
    
    @Test
    void getInProductionCount() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 10, 5, 3, 0, 0, 0, 0, 0, 0, 0, null);
        assertEquals(2, s.getInProductionCount());
    }
    
    @Test
    void getSuccessRateTotalTerminatedZeroReturnsOne() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 10, 0, 0, 0, 0, 0, 0, 0, 0, 0, null);
        assertEquals(1.0, s.getSuccessRate());
    }
    
    @Test
    void getSuccessRateMixed() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 10, 10, 10, 0, 0, 6, 4, 10, 0, 0, null);
        assertEquals(0.6, s.getSuccessRate());
    }
    
    @Test
    void getPassiveEgressByReasonNullOrEmptyReturnsZero() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null);
        assertEquals(0L, s.getPassiveEgressByReason(REASON_TIMEOUT));
        assertEquals(0L, s.getPassiveEgressByReason(null));
    }
    
    @Test
    void getPassiveEgressByReasonWithMap() {
        FlowProgressSnapshot s =
                new FlowProgressSnapshot("j1", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Map.of(REASON_TIMEOUT, 3L, "EVICTION", 1L));
        assertEquals(3L, s.getPassiveEgressByReason(REASON_TIMEOUT));
        assertEquals(1L, s.getPassiveEgressByReason("EVICTION"));
        assertEquals(0L, s.getPassiveEgressByReason("MISMATCH"));
    }
    
    @Test
    void getTpsDurationZeroReturnsZero() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 0, 0, 0, 0, 0, 0, 0, 0, 100, 100, null);
        assertEquals(0.0, s.getTps());
    }
    
    @Test
    void getTpsPositiveDuration() {
        // 100 terminated over 1000 seconds => 0.1 TPS
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 0, 0, 0, 0, 0, 0, 0, 100, 0, 1_000_000L, null);
        assertEquals(0.1, s.getTps());
    }
}
