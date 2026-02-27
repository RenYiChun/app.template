package com.lrenyi.template.flow.engine;

import java.util.Optional;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowInlet;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowSource;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.exception.FlowExceptionHelper;
import com.lrenyi.template.flow.exception.FlowPhase;
import com.lrenyi.template.flow.internal.DefaultProgressTracker;
import com.lrenyi.template.flow.internal.FlowInletImpl;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
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
    private final FlowManager flowManager;

    private MeterRegistry registry() {
        return flowManager.getMeterRegistry();
    }

    public <T> void run(String jobId, FlowJoiner<T> joiner, long total, TemplateConfigProperties.Flow flowConfig) {
        DefaultProgressTracker tracker = new DefaultProgressTracker(jobId, flowManager);
        tracker.setTotalExpected(jobId, total);
        run(jobId, joiner, tracker, flowConfig);
    }

    public <T> void run(String jobId,
            FlowJoiner<T> joiner,
            ProgressTracker tracker,
            TemplateConfigProperties.Flow jc) {
        log.info("驱动流聚合任务开始: {}", jobId);

        Counter.builder(FlowMetricNames.JOB_STARTED)
               .tag(FlowMetricNames.TAG_JOB_ID, jobId)
               .register(registry())
               .increment();

        try {
            FlowLauncher<T> launcher = flowManager.createLauncher(jobId, joiner, tracker, jc);

            try (FlowSourceProvider<T> provider = joiner.sourceProvider()) {
                runUntilNoMoreSubSources(provider, jobId, launcher);
            }

            Counter.builder(FlowMetricNames.JOB_COMPLETED)
                   .tag(FlowMetricNames.TAG_JOB_ID, jobId)
                   .register(registry())
                   .increment();
        } catch (Exception e) {
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.PRODUCTION);
            Counter.builder(FlowMetricNames.ERRORS)
                   .tag(FlowMetricNames.TAG_ERROR_TYPE, "job_failed")
                   .tag(FlowMetricNames.TAG_PHASE, "PRODUCTION")
                   .register(registry())
                   .increment();
            throw e;
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
        DefaultProgressTracker tracker = new DefaultProgressTracker(jobId, flowManager);
        tracker.setTotalExpected(jobId, total);
        run(jobId, joiner, singleSource, tracker, flowConfig);
    }

    public <T> void run(String jobId,
            FlowJoiner<T> joiner,
            FlowSource<T> singleSource,
            ProgressTracker tracker,
            TemplateConfigProperties.Flow jc) {
        log.info("驱动流聚合任务开始（单流）: {}", jobId);

        FlowLauncher<T> launcher = flowManager.createLauncher(jobId, joiner, tracker, jc);

        try (FlowSourceProvider<T> provider = FlowSourceAdapters.singleSourceProvider(singleSource)) {
            runUntilNoMoreSubSources(provider, jobId, launcher);
        }
    }

    private <T> void runUntilNoMoreSubSources(FlowSourceProvider<T> provider,
            String jobId,
            FlowLauncher<T> launcher) {
        while (tryRunNextSubSource(provider, jobId, launcher)) {
            Thread.onSpinWait();
        }
        launcher.getTaskOrchestrator().tracker().markSourceFinished(jobId);
    }

    private <T> boolean tryRunNextSubSource(FlowSourceProvider<T> provider,
            String jobId,
            FlowLauncher<T> launcher) {
        try {
            if (!provider.hasNextSubSource()) {
                return false;
            }
        } catch (InterruptedException e) {
            log.warn("获取子流过程中被中断", e);
            Thread.currentThread().interrupt();
            return false;
        }
        launcher.getProducerExecutor().submit(() -> runSubSourceInVirtualThread(provider, launcher, jobId));
        return true;
    }

    private <T> void runSubSourceInVirtualThread(FlowSourceProvider<T> provider,
            FlowLauncher<T> launcher,
            String jobId) {
        FlowSource<T> sub = provider.nextSubSource();
        try (sub) {
            drainSubSource(sub, launcher);
        } catch (Exception e) {
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.PRODUCTION);
            Counter.builder(FlowMetricNames.ERRORS)
                   .tag(FlowMetricNames.TAG_ERROR_TYPE, "subsource_failed")
                   .tag(FlowMetricNames.TAG_PHASE, "PRODUCTION")
                   .register(registry())
                   .increment();
            log.error("子流消费异常 jobId={}", jobId, e);
        }
    }
    
    private <T> void drainSubSource(FlowSource<T> sub, FlowLauncher<T> launcher) {
        Optional<T> item = pollNext(sub);
        while (item.isPresent()) {
            if (launcher.isStopped()) {
                launcher.getTaskOrchestrator().tracker().onProductionReleased();
                Counter.builder(FlowMetricNames.ERRORS)
                       .tag(FlowMetricNames.TAG_ERROR_TYPE, "job_stopped")
                       .tag(FlowMetricNames.TAG_PHASE, "PRODUCTION")
                       .register(registry())
                       .increment();
                return;
            }
            launcher.launch(item.get());
            item = pollNext(sub);
        }
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
     * 推送模式：注册任务并返回 FlowInlet
     */
    public <T> FlowInlet<T> startPush(String jobId,
            FlowJoiner<T> joiner,
            TemplateConfigProperties.Flow flowConfig) {
        return startPush(jobId, joiner, -1, flowConfig);
    }

    public <T> FlowInlet<T> startPush(String jobId,
            FlowJoiner<T> joiner,
            long total,
            TemplateConfigProperties.Flow flowConfig) {
        DefaultProgressTracker tracker = new DefaultProgressTracker(jobId, flowManager);
        tracker.setTotalExpected(jobId, total);
        FlowLauncher<T> launcher = flowManager.createLauncher(jobId, joiner, tracker, flowConfig);
        return new FlowInletImpl<>(launcher);
    }

    public ProgressTracker getProgressTracker(String jobId) {
        return flowManager.getActiveLauncher(jobId).getTaskOrchestrator().tracker();
    }
}
