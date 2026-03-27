package com.lrenyi.template.flow.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/**
 * 终态指标独立保留 7 天，避免阶段 live gauge 注销后无法回看历史任务。
 */
public final class FlowTerminalMetrics {
    private static final long TTL_MILLIS = TimeUnit.DAYS.toMillis(7);

    private final MeterRegistry meterRegistry;
    private final ScheduledExecutorService scheduler;
    private final Map<String, TerminalMetricState> jobStates = new ConcurrentHashMap<>();
    private final Map<String, TerminalMetricState> stageStates = new ConcurrentHashMap<>();

    public FlowTerminalMetrics(MeterRegistry meterRegistry, ScheduledExecutorService scheduler) {
        this.meterRegistry = meterRegistry;
        this.scheduler = scheduler;
    }

    public void markJobRunning(String rootJobId, String displayName, long startTimeMillis) {
        String key = rootJobId;
        TerminalMetricState state = jobStates.computeIfAbsent(key,
                unused -> registerState(FlowMetricTags.resolve(rootJobId, displayName), true));
        state.markRunning(startTimeMillis);
    }

    public void markJobTerminal(String rootJobId,
                                String displayName,
                                long startTimeMillis,
                                long endTimeMillis) {
        String key = rootJobId;
        TerminalMetricState state = jobStates.computeIfAbsent(key,
                unused -> registerState(FlowMetricTags.resolve(rootJobId, displayName), true));
        state.markTerminal(startTimeMillis, endTimeMillis);
        scheduleCleanup(jobStates, key, state, endTimeMillis);
    }

    public void markStageRunning(String internalJobId,
                                 String metricJobId,
                                 String stageDisplayName,
                                 long startTimeMillis) {
        String key = internalJobId;
        TerminalMetricState state = stageStates.computeIfAbsent(key,
                unused -> registerState(FlowMetricTags.resolve(internalJobId, metricJobId, stageDisplayName), false));
        state.markRunning(startTimeMillis);
    }

    public void markStageTerminal(String internalJobId,
                                  String metricJobId,
                                  String stageDisplayName,
                                  long startTimeMillis,
                                  long endTimeMillis) {
        String key = internalJobId;
        TerminalMetricState state = stageStates.computeIfAbsent(key,
                unused -> registerState(FlowMetricTags.resolve(internalJobId, metricJobId, stageDisplayName), false));
        state.markTerminal(startTimeMillis, endTimeMillis);
        scheduleCleanup(stageStates, key, state, endTimeMillis);
    }

    private TerminalMetricState registerState(FlowMetricTags tags, boolean jobLevel) {
        TerminalMetricState state = new TerminalMetricState(meterRegistry, tags, jobLevel);
        state.register();
        return state;
    }

    private void scheduleCleanup(Map<String, TerminalMetricState> owner,
                                 String key,
                                 TerminalMetricState state,
                                 long endTimeMillis) {
        scheduler.schedule(() -> {
            TerminalMetricState existing = owner.get(key);
            if (existing == null || existing != state || existing.endTimeSeconds.get() != toSeconds(endTimeMillis)) {
                return;
            }
            if (owner.remove(key, existing)) {
                existing.unregister();
            }
        }, TTL_MILLIS, TimeUnit.MILLISECONDS);
    }

    private static long toSeconds(long millis) {
        return Math.max(0L, TimeUnit.MILLISECONDS.toSeconds(millis));
    }

    private static final class TerminalMetricState {
        private final MeterRegistry meterRegistry;
        private final FlowMetricTags tags;
        private final boolean jobLevel;
        private final AtomicLong startTimeSeconds = new AtomicLong(0L);
        private final AtomicLong endTimeSeconds = new AtomicLong(0L);
        private final AtomicLong durationSeconds = new AtomicLong(0L);
        private final List<Meter> meters = new ArrayList<>();

        private TerminalMetricState(MeterRegistry meterRegistry, FlowMetricTags tags, boolean jobLevel) {
            this.meterRegistry = meterRegistry;
            this.tags = tags;
            this.jobLevel = jobLevel;
        }

        private void register() {
            String startMetric = jobLevel ? FlowMetricNames.JOB_START_TIME_SECONDS : FlowMetricNames.STAGE_START_TIME_SECONDS;
            String endMetric = jobLevel ? FlowMetricNames.JOB_END_TIME_SECONDS : FlowMetricNames.STAGE_END_TIME_SECONDS;
            String durationMetric = jobLevel ? FlowMetricNames.JOB_DURATION_SECONDS : FlowMetricNames.STAGE_DURATION_SECONDS;

            meters.add(Gauge.builder(startMetric, startTimeSeconds, AtomicLong::get)
                    .tags(baseTags())
                    .register(meterRegistry));
            meters.add(Gauge.builder(endMetric, endTimeSeconds, AtomicLong::get)
                    .tags(baseTags())
                    .register(meterRegistry));
            meters.add(Gauge.builder(durationMetric, durationSeconds, AtomicLong::get)
                    .tags(baseTags())
                    .register(meterRegistry));
        }

        private void markRunning(long startTimeMillis) {
            startTimeSeconds.compareAndSet(0L, toSeconds(startTimeMillis));
        }

        private void markTerminal(long startTimeMillis, long endTimeMillis) {
            startTimeSeconds.compareAndSet(0L, toSeconds(startTimeMillis));
            endTimeSeconds.set(toSeconds(endTimeMillis));
            long duration = Math.max(0L, endTimeSeconds.get() - startTimeSeconds.get());
            durationSeconds.set(duration);
        }

        private Tags baseTags() {
            return jobLevel
                    ? Tags.of(
                            FlowMetricNames.TAG_ROOT_JOB_ID, tags.rootJobId(),
                            FlowMetricNames.TAG_ROOT_JOB_DISPLAY_NAME, tags.rootJobDisplayName(),
                            FlowMetricNames.TAG_DISPLAY_NAME, tags.displayName())
                    : Tags.of(
                            FlowMetricNames.TAG_ROOT_JOB_ID, tags.rootJobId(),
                            FlowMetricNames.TAG_ROOT_JOB_DISPLAY_NAME, tags.rootJobDisplayName(),
                            FlowMetricNames.TAG_STAGE_KEY, tags.stageKey(),
                            FlowMetricNames.TAG_STAGE_NAME, tags.stageName(),
                            FlowMetricNames.TAG_STAGE_DISPLAY_NAME, tags.stageDisplayName(),
                            FlowMetricNames.TAG_DISPLAY_NAME, tags.displayName());
        }

        private void unregister() {
            for (Meter meter : meters) {
                meterRegistry.remove(meter);
            }
            meters.clear();
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }
}
