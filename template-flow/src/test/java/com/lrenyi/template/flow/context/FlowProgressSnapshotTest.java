package com.lrenyi.template.flow.context;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlowProgressSnapshotTest {

    @Test
    void constructor_nullPassiveEgressByReason_usesEmptyMap() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 10, 5, 3, 2, 1, 2, 1, 3, 0, 0, null);
        assertEquals(0, s.passiveEgressByReason().size());
    }

    @Test
    void constructor_nonNullPassiveEgressByReason_unmodifiable() {
        Map<String, Long> map = new java.util.HashMap<>(Map.of("TIMEOUT", 1L));
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, map);
        assertEquals(1, s.passiveEgressByReason().size());
        assertThrows(UnsupportedOperationException.class, () -> s.passiveEgressByReason().put("x", 1L));
    }

    @Test
    void getCompletionRate_totalExpectedPositive() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 10, 10, 10, 0, 0, 8, 2, 10, 0, 0, null);
        assertEquals(1.0, s.getCompletionRate());
        FlowProgressSnapshot s2 = new FlowProgressSnapshot("j1", 10, 5, 3, 2, 1, 1, 1, 2, 0, 0, null);
        assertEquals(0.2, s2.getCompletionRate());
    }

    @Test
    void getCompletionRate_totalExpectedZero_endTimeSet_returnsOne() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 0, 5, 5, 0, 0, 5, 0, 5, 100, 200, null);
        assertEquals(1.0, s.getCompletionRate());
    }

    @Test
    void getCompletionRate_totalExpectedZero_productionAcquiredZero_returnsZero() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null);
        assertEquals(0.0, s.getCompletionRate());
    }

    @Test
    void getCompletionRate_totalExpectedZero_terminatedOverAcquired() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 0, 10, 10, 0, 0, 8, 2, 10, 0, 0, null);
        assertEquals(1.0, s.getCompletionRate());
    }

    @Test
    void getInProductionCount() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 10, 5, 3, 0, 0, 0, 0, 0, 0, 0, null);
        assertEquals(2, s.getInProductionCount());
    }

    @Test
    void getSuccessRate_totalTerminatedZero_returnsOne() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 10, 0, 0, 0, 0, 0, 0, 0, 0, 0, null);
        assertEquals(1.0, s.getSuccessRate());
    }

    @Test
    void getSuccessRate_mixed() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 10, 10, 10, 0, 0, 6, 4, 10, 0, 0, null);
        assertEquals(0.6, s.getSuccessRate());
    }

    @Test
    void getPassiveEgressByReason_nullOrEmpty_returnsZero() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null);
        assertEquals(0L, s.getPassiveEgressByReason("TIMEOUT"));
        assertEquals(0L, s.getPassiveEgressByReason(null));
    }

    @Test
    void getPassiveEgressByReason_withMap() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                Map.of("TIMEOUT", 3L, "EVICTION", 1L));
        assertEquals(3L, s.getPassiveEgressByReason("TIMEOUT"));
        assertEquals(1L, s.getPassiveEgressByReason("EVICTION"));
        assertEquals(0L, s.getPassiveEgressByReason("MISMATCH"));
    }

    @Test
    void getTps_durationZero_returnsZero() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 0, 0, 0, 0, 0, 0, 0, 0, 100, 100, null);
        assertEquals(0.0, s.getTps());
    }

    @Test
    void getTps_positiveDuration() {
        // 100 terminated over 1000 seconds => 0.1 TPS
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 0, 0, 0, 0, 0, 0, 0, 100, 0, 1_000_000L, null);
        assertEquals(0.1, s.getTps());
    }
}
