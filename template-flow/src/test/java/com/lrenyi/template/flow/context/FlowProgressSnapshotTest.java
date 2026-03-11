package com.lrenyi.template.flow.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlowProgressSnapshotTest {

    @Test
    void getCompletionRateTotalExpectedPositive() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 10, 10, 10, 0, 0, 10, 0, 0);
        assertEquals(1.0, s.getCompletionRate());
        FlowProgressSnapshot s2 = new FlowProgressSnapshot("j1", 10, 5, 3, 2, 1, 2, 0, 0);
        assertEquals(0.2, s2.getCompletionRate());
    }

    @Test
    void getCompletionRateTotalExpectedZeroEndTimeSetReturnsOne() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 0, 5, 5, 0, 0, 5, 100, 200);
        assertEquals(1.0, s.getCompletionRate());
    }

    @Test
    void getCompletionRateTotalExpectedZeroProductionAcquiredZeroReturnsZero() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 0, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(0.0, s.getCompletionRate());
    }

    @Test
    void getCompletionRateTotalExpectedZeroTerminatedOverAcquired() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 0, 10, 10, 0, 0, 10, 0, 0);
        assertEquals(1.0, s.getCompletionRate());
    }

    @Test
    void getInProductionCount() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 10, 5, 3, 0, 0, 0, 0, 0);
        assertEquals(2, s.getInProductionCount());
    }

    @Test
    void getSuccessRateReturnsOne() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 10, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(1.0, s.getSuccessRate());
    }

    @Test
    void getPassiveEgressByReasonReturnsZero() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 0, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(0L, s.getPassiveEgressByReason("TIMEOUT"));
        assertEquals(0L, s.getPassiveEgressByReason(null));
    }

    @Test
    void getTpsDurationZeroReturnsZero() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 0, 0, 0, 0, 0, 0, 100, 100);
        assertEquals(0.0, s.getTps());
    }

    @Test
    void getTpsPositiveDuration() {
        FlowProgressSnapshot s = new FlowProgressSnapshot("j1", 0, 0, 0, 0, 0, 100, 0, 1_000_000L);
        assertEquals(0.1, s.getTps());
    }
}
