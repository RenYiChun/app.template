package com.lrenyi.template.flow.engine;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowInlet;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowSource;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.backpressure.DimensionLease;
import com.lrenyi.template.flow.backpressure.dimension.ProducerConcurrencyDimension;
import com.lrenyi.template.flow.context.FlowProgressSnapshot;
import com.lrenyi.template.flow.exception.FlowExceptionHelper;
import com.lrenyi.template.flow.exception.FlowPhase;
import com.lrenyi.template.flow.internal.DefaultProgressTracker;
import com.lrenyi.template.flow.internal.FlowInletImpl;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.metrics.FlowMetricTags;
import com.lrenyi.template.flow.model.FlowConstants;
import com.lrenyi.template.flow.util.FlowLogHelper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 通用流聚合引擎
 */
@Slf4j
@RequiredArgsConstructor
public class FlowJoinerEngine {
    private static final String PHASE_PRODUCTION = "PRODUCTION";
    private final FlowManager flowManager;

    public <T> void run(String jobId, FlowJoiner<T> joiner, long total, TemplateConfigProperties.Flow flowConfig) {
        run(jobId, null, joiner, total, flowConfig);
    }

    public <T> void run(String jobId,
        String displayName,
        FlowJoiner<T> joiner,
        long total,
        TemplateConfigProperties.Flow flowConfig) {
        DefaultProgressTracker tracker = new DefaultProgressTracker(jobId, flowManager);
        tracker.setTotalExpected(jobId, total);
        run(jobId, displayName, joiner, tracker, flowConfig);
    }

    public <T> void run(String jobId, FlowJoiner<T> joiner, ProgressTracker tracker, TemplateConfigProperties.Flow jc) {
        run(jobId, null, joiner, tracker, jc);
    }

    public <T> void run(String jobId,
        String displayName,
        FlowJoiner<T> joiner,
        ProgressTracker tracker,
        TemplateConfigProperties.Flow jc) {
        log.info("驱动流聚合任务开始: {}", FlowLogHelper.formatJobContext(jobId, displayName));
        try {
            FlowLauncher<T> launcher = flowManager.createLauncher(jobId, displayName, joiner, tracker, jc);

            try (FlowSourceProvider<T> provider = joiner.sourceProvider()) {
                runUntilNoMoreSubSources(provider, jobId, launcher);
            }
        } catch (Exception e) {
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.PRODUCTION, "job_failed");
            throw e;
        }
    }

    private MeterRegistry registry() {
        return flowManager.getMeterRegistry();
    }

    private <T> void runUntilNoMoreSubSources(FlowSourceProvider<T> provider, String jobId, FlowLauncher<T> launcher) {
        long startMillis = System.currentTimeMillis();
        AtomicInteger activeSubSources = new AtomicInteger(0);
        AtomicInteger submittedSubSources = new AtomicInteger(0);
        while (tryRunNextSubSource(provider, jobId, launcher, activeSubSources, submittedSubSources)) {
            Thread.onSpinWait();
        }
        awaitSubSourcesFinished(activeSubSources);
        awaitInProductionDrained(launcher);
        launcher.getTracker().markSourceFinished(jobId, true);
        launcher.getStorage().triggerCompletionDrain();
        log.info("子流生产阶段结束, {}, submittedSubSources={}, elapsedMs={}",
                 FlowLogHelper.formatJobContext(jobId, launcher.getMetricJobId()),
                 submittedSubSources.get(),
                 System.currentTimeMillis() - startMillis
        );
    }

    private <T> void awaitInProductionDrained(FlowLauncher<T> launcher) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(FlowConstants.DEFAULT_ACQUIRE_TIMEOUT_MS);
        while (!launcher.isStopped()) {
            FlowProgressSnapshot snapshot = launcher.getTracker().getSnapshot();
            if (snapshot.getInProductionCount() <= 0) {
                return;
            }
            if (System.nanoTime() >= deadline) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void awaitSubSourcesFinished(AtomicInteger activeSubSources) {
        while (activeSubSources.get() > 0) {
            LockSupport.parkNanos(1_000_000L);
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private <T> boolean tryRunNextSubSource(FlowSourceProvider<T> provider,
        String jobId,
        FlowLauncher<T> launcher,
        AtomicInteger activeSubSources,
        AtomicInteger submittedSubSources) {
        try {
            if (!provider.hasNextSubSource()) {
                return false;
            }
        } catch (InterruptedException e) {
            log.warn("获取子流过程中被中断", e);
            Thread.currentThread().interrupt();
            return false;
        }
        // 在引擎循环线程（调用方）申请生产并发席位，对拉取速率形成背压；
        // 任务在虚拟线程 finally 中释放，控制同时执行子流拉取的并发数。
        DimensionLease producerLease;
        try {
            producerLease = launcher.getBackpressureManager().acquire(ProducerConcurrencyDimension.ID, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            FlowExceptionHelper.handleException(jobId,
                                                null,
                                                e,
                                                FlowPhase.STORAGE,
                                                "producer_concurrency_acquire_interrupted"
            );
            return false;
        } catch (TimeoutException e) {
            FlowExceptionHelper.handleException(jobId,
                                                null,
                                                e,
                                                FlowPhase.STORAGE,
                                                "producer_concurrency_acquire_timeout"
            );
            return false;
        }
        int subSourceSeq = submittedSubSources.incrementAndGet();
        activeSubSources.incrementAndGet();
        launcher.getProducerExecutor().submit(() -> {
            try {
                runSubSourceInVirtualThread(provider, launcher, jobId, subSourceSeq);
            } finally {
                producerLease.close();
                activeSubSources.decrementAndGet();
            }
        });
        return true;
    }

    private <T> void runSubSourceInVirtualThread(FlowSourceProvider<T> provider,
        FlowLauncher<T> launcher,
        String jobId,
        int subSourceSeq) {
        FlowSource<T> sub = provider.nextSubSource();
        long startMillis = System.currentTimeMillis();
        try (sub) {
            long pulledCount = drainSubSource(sub, launcher);
            log.info("子流处理完成, {}, subSourceSeq={}, pulledCount={}, elapsedMs={}",
                     FlowLogHelper.formatJobContext(jobId, launcher.getMetricJobId()),
                     subSourceSeq,
                     pulledCount,
                     System.currentTimeMillis() - startMillis
            );
        } catch (Exception e) {
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.PRODUCTION, "subsource_failed");
            log.error("子流消费异常, {}, subSourceSeq={}",
                      FlowLogHelper.formatJobContext(jobId, launcher.getMetricJobId()),
                      subSourceSeq,
                      e
            );
        }
    }

    private <T> long drainSubSource(FlowSource<T> sub, FlowLauncher<T> launcher) {
        Optional<T> item = pollNext(sub);
        long pulledCount = 0L;
        while (item.isPresent()) {
            if (launcher.isStopped()) {
                launcher.getTracker().onProductionReleased();
                Counter.builder(FlowMetricNames.ERRORS)
                       .tags(FlowMetricTags.resolve(launcher.getJobId(), launcher.getMetricJobId()).toTags())
                       .tag(FlowMetricNames.TAG_ERROR_TYPE, "job_stopped")
                       .tag(FlowMetricNames.TAG_PHASE, PHASE_PRODUCTION)
                       .register(registry())
                       .increment();
                log.warn("子流处理提前停止, {}, pulledCount={}",
                         FlowLogHelper.formatJobContext(launcher.getJobId(), launcher.getMetricJobId()),
                         pulledCount
                );
                return pulledCount;
            }
            launcher.launch(item.get());
            pulledCount++;
            item = pollNext(sub);
        }
        return pulledCount;
    }

    private <T> Optional<T> pollNext(FlowSource<T> sub) {
        try {
            if (!sub.hasNext()) {
                return Optional.empty();
            }
            return Optional.of(sub.next());
        } catch (InterruptedException e) {
            log.warn("子流拉取过程中被中断", e);
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /**
     * 单流 run：业务只有一条流时直接传入 FlowSource
     */
    public <T> void run(String jobId,
        FlowJoiner<T> joiner,
        FlowSource<T> singleSource,
        long total,
        TemplateConfigProperties.Flow flowConfig) {
        run(jobId, null, joiner, singleSource, total, flowConfig);
    }

    public <T> void run(String jobId,
        String displayName,
        FlowJoiner<T> joiner,
        FlowSource<T> singleSource,
        long total,
        TemplateConfigProperties.Flow flowConfig) {
        DefaultProgressTracker tracker = new DefaultProgressTracker(jobId, flowManager);
        tracker.setTotalExpected(jobId, total);
        run(jobId, displayName, joiner, singleSource, tracker, flowConfig);
    }

    public <T> void run(String jobId,
        FlowJoiner<T> joiner,
        FlowSource<T> singleSource,
        ProgressTracker tracker,
        TemplateConfigProperties.Flow jc) {
        run(jobId, null, joiner, singleSource, tracker, jc);
    }

    public <T> void run(String jobId,
        String displayName,
        FlowJoiner<T> joiner,
        FlowSource<T> singleSource,
        ProgressTracker tracker,
        TemplateConfigProperties.Flow jc) {
        log.info("驱动流聚合任务开始（单流）: {}", FlowLogHelper.formatJobContext(jobId, displayName));

        FlowLauncher<T> launcher = flowManager.createLauncher(jobId, displayName, joiner, tracker, jc);

        try (FlowSourceProvider<T> provider = FlowSourceAdapters.singleSourceProvider(singleSource)) {
            runUntilNoMoreSubSources(provider, jobId, launcher);
        }
    }

    /**
     * 推送模式：注册任务并返回 FlowInlet
     */
    public <T> FlowInlet<T> startPush(String jobId, FlowJoiner<T> joiner, TemplateConfigProperties.Flow flowConfig) {
        return startPush(jobId, null, joiner, -1, flowConfig);
    }

    public <T> FlowInlet<T> startPush(String jobId,
        FlowJoiner<T> joiner,
        long total,
        TemplateConfigProperties.Flow flowConfig) {
        return startPush(jobId, null, joiner, total, flowConfig);
    }

    public <T> FlowInlet<T> startPush(String jobId,
        String displayName,
        FlowJoiner<T> joiner,
        long total,
        TemplateConfigProperties.Flow flowConfig) {
        DefaultProgressTracker tracker = new DefaultProgressTracker(jobId, flowManager);
        tracker.setTotalExpected(jobId, total);
        FlowLauncher<T> launcher = flowManager.createLauncher(jobId, displayName, joiner, tracker, flowConfig);
        log.info("flow source data push model, {}, totalExpected={}",
                 FlowLogHelper.formatJobContext(jobId, displayName),
                 total
        );
        FlowInletImpl<T> inlet = new FlowInletImpl<>(launcher);
        launcher.setInFlightPushCountSupplier(inlet::getInFlightPushCount);
        return inlet;
    }

    public ProgressTracker getProgressTracker(String jobId) {
        return flowManager.getProgressTracker(jobId);
    }
}
