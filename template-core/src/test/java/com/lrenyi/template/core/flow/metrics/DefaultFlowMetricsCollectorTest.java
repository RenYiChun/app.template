package com.lrenyi.template.core.flow.metrics;

import java.util.Map;
import com.lrenyi.template.core.flow.FailureReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 监控指标收集器单元测试：验证 counters、latencies、failureReasons、resources、errors 的准确性。
 */
class DefaultFlowMetricsCollectorTest {

    private DefaultFlowMetricsCollector collector;

    @BeforeEach
    void setUp() {
        collector = new DefaultFlowMetricsCollector();
    }

    @Test
    void incrementCounter_accumulatesCorrectly() {
        collector.incrementCounter("job_started");
        collector.incrementCounter("job_started");
        collector.incrementCounter("job_started");
        collector.incrementCounter("launcher_created", 2);

        Map<String, Object> metrics = collector.getMetrics();
        @SuppressWarnings("unchecked")
        Map<String, Long> counters = (Map<String, Long>) metrics.get("counters");
        assertNotNull(counters);
        assertEquals(3L, counters.get("job_started"));
        assertEquals(2L, counters.get("launcher_created"));
    }

    @Test
    void recordLatency_producesCorrectStats() {
        collector.recordLatency("deposit", 10);
        collector.recordLatency("deposit", 20);
        collector.recordLatency("deposit", 30);

        Map<String, Object> metrics = collector.getMetrics();
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> latencies = (Map<String, Map<String, Object>>) metrics.get("latencies");
        assertNotNull(latencies);
        Map<String, Object> deposit = latencies.get("deposit");
        assertNotNull(deposit);
        assertEquals(3, deposit.get("count"));
        assertEquals(10L, deposit.get("min"));
        assertEquals(30L, deposit.get("max"));
        assertEquals(20.0, deposit.get("avg"));
        assertEquals(20.0, ((Number) deposit.get("median")).doubleValue(), 0.01);
    }

    @Test
    void recordFailureReason_aggregatesByReason() {
        collector.recordFailureReason(FailureReason.REPLACE, "job-1");
        collector.recordFailureReason(FailureReason.REPLACE, "job-2");
        collector.recordFailureReason(FailureReason.MISMATCH, "job-1");
        collector.recordFailureReason(FailureReason.SHUTDOWN, null);

        Map<String, Object> metrics = collector.getMetrics();
        @SuppressWarnings("unchecked")
        Map<String, Long> failureReasons = (Map<String, Long>) metrics.get("failureReasons");
        assertNotNull(failureReasons);
        assertEquals(2L, failureReasons.get(FailureReason.REPLACE.name()));
        assertEquals(1L, failureReasons.get(FailureReason.MISMATCH.name()));
        assertEquals(1L, failureReasons.get(FailureReason.SHUTDOWN.name()));

        @SuppressWarnings("unchecked")
        Map<String, Long> errors = (Map<String, Long>) metrics.get("errors");
        assertNotNull(errors);
        assertTrue(errors.containsKey("onFailed_REPLACE:job-1"));
        assertTrue(errors.containsKey("onFailed_REPLACE:job-2"));
        assertTrue(errors.containsKey("onFailed_MISMATCH:job-1"));
        assertTrue(errors.containsKey("onFailed_SHUTDOWN:unknown"));
    }

    @Test
    void recordResourceUsage_overwritesByKey() {
        collector.recordResourceUsage("active_launchers", 5);
        collector.recordResourceUsage("active_launchers", 10);
        collector.recordResourceUsage("semaphore_available", 100);

        Map<String, Object> metrics = collector.getMetrics();
        @SuppressWarnings("unchecked")
        Map<String, Long> resources = (Map<String, Long>) metrics.get("resources");
        assertNotNull(resources);
        assertEquals(10L, resources.get("active_launchers"));
        assertEquals(100L, resources.get("semaphore_available"));
    }

    @Test
    void recordError_aggregatesByErrorTypeAndJobId() {
        collector.recordError("job_stopped", "job-a");
        collector.recordError("job_stopped", "job-a");
        collector.recordError("job_stopped", "job-b");
        collector.recordError("deposit_failed", "job-a");

        Map<String, Object> metrics = collector.getMetrics();
        @SuppressWarnings("unchecked")
        Map<String, Long> errors = (Map<String, Long>) metrics.get("errors");
        assertNotNull(errors);
        assertEquals(2L, errors.get("job_stopped:job-a"));
        assertEquals(1L, errors.get("job_stopped:job-b"));
        assertEquals(1L, errors.get("deposit_failed:job-a"));
    }

    @Test
    void recordFailureReason_withNullReason_doesNotIncrement() {
        collector.recordFailureReason(null, "job-1");
        Map<String, Object> metrics = collector.getMetrics();
        @SuppressWarnings("unchecked")
        Map<String, Long> failureReasons = (Map<String, Long>) metrics.get("failureReasons");
        assertNotNull(failureReasons);
        assertTrue(failureReasons.isEmpty());
    }

    @Test
    void reset_clearsAllMetrics() {
        collector.incrementCounter("x");
        collector.recordLatency("y", 1);
        collector.recordFailureReason(FailureReason.TIMEOUT, "j");
        collector.recordResourceUsage("r", 1);
        collector.recordError("e", "j");

        collector.reset();

        Map<String, Object> metrics = collector.getMetrics();
        @SuppressWarnings("unchecked")
        Map<String, Long> counters = (Map<String, Long>) metrics.get("counters");
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> latencies = (Map<String, Map<String, Object>>) metrics.get("latencies");
        @SuppressWarnings("unchecked")
        Map<String, Long> failureReasons = (Map<String, Long>) metrics.get("failureReasons");
        @SuppressWarnings("unchecked")
        Map<String, Long> resources = (Map<String, Long>) metrics.get("resources");
        @SuppressWarnings("unchecked")
        Map<String, Long> errors = (Map<String, Long>) metrics.get("errors");

        assertTrue(counters == null || counters.isEmpty());
        assertTrue(latencies == null || latencies.isEmpty());
        assertTrue(failureReasons == null || failureReasons.isEmpty());
        assertTrue(resources == null || resources.isEmpty());
        assertTrue(errors == null || errors.isEmpty());
    }

    @Test
    void getMetrics_containsExpectedKeys() {
        collector.incrementCounter("c");
        Map<String, Object> metrics = collector.getMetrics();
        assertTrue(metrics.containsKey("counters"));
        assertTrue(metrics.containsKey("latencies"));
        assertTrue(metrics.containsKey("resources"));
        assertTrue(metrics.containsKey("errors"));
        assertTrue(metrics.containsKey("failureReasons"));
    }
}
