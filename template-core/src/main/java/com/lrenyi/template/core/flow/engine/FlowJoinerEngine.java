package com.lrenyi.template.core.flow.engine;

import java.util.Map;
import java.util.Optional;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.flow.api.FlowInlet;
import com.lrenyi.template.core.flow.api.FlowJoiner;
import com.lrenyi.template.core.flow.api.FlowSource;
import com.lrenyi.template.core.flow.api.FlowSourceAdapters;
import com.lrenyi.template.core.flow.api.FlowSourceProvider;
import com.lrenyi.template.core.flow.api.ProgressTracker;
import com.lrenyi.template.core.flow.exception.FlowExceptionHelper;
import com.lrenyi.template.core.flow.exception.FlowPhase;
import com.lrenyi.template.core.flow.internal.DefaultProgressTracker;
import com.lrenyi.template.core.flow.internal.FlowInletImpl;
import com.lrenyi.template.core.flow.internal.FlowLauncher;
import com.lrenyi.template.core.flow.manager.FlowManager;
import com.lrenyi.template.core.flow.metrics.FlowMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 通用流聚合引擎
 */
@Slf4j
@RequiredArgsConstructor
public class FlowJoinerEngine {
    private final FlowManager flowManager;

    public <T> void run(String jobId, FlowJoiner<T> joiner, long total, TemplateConfigProperties.JobConfig jobConfig) {
        DefaultProgressTracker tracker = new DefaultProgressTracker(jobId, flowManager);
        tracker.setTotalExpected(jobId, total);
        run(jobId, joiner, tracker, jobConfig);
    }

    public <T> void run(String jobId,
                        FlowJoiner<T> joiner,
                        ProgressTracker tracker,
                        TemplateConfigProperties.JobConfig jc) {
        log.info("驱动流聚合任务开始: {}", jobId);
        long jobStartTime = System.currentTimeMillis();
        FlowMetrics.incrementCounter("job_started");

        try {
            FlowLauncher<T> launcher = flowManager.createLauncher(jobId, joiner, tracker, jc);

            try (FlowSourceProvider<T> provider = joiner.sourceProvider()) {
                runUntilNoMoreSubSources(provider, jobId, launcher);
            }

            long jobDuration = System.currentTimeMillis() - jobStartTime;
            FlowMetrics.recordLatency("job_total_duration", jobDuration);
            FlowMetrics.incrementCounter("job_completed");
        } catch (Exception e) {
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.PRODUCTION);
            FlowMetrics.recordError("job_failed", jobId);
            throw e;
        }
    }

    /**
     * 单流 run：业务只有一条流时直接传入 FlowSource，引擎内部包装为「仅含一个子流」的 Provider 并复用拉取逻辑。
     * 不调用 joiner.sourceProvider()。
     *
     * @param total 预期总条数，用于进度；若未知可传 -1
     */
    public <T> void run(String jobId,
                        FlowJoiner<T> joiner,
                        FlowSource<T> singleSource,
                        long total,
                        TemplateConfigProperties.JobConfig jobConfig) {
        DefaultProgressTracker tracker = new DefaultProgressTracker(jobId, flowManager);
        tracker.setTotalExpected(jobId, total);
        run(jobId, joiner, singleSource, tracker, jobConfig);
    }

    /**
     * 单流 run（指定 ProgressTracker）：单条 FlowSource + 已有 tracker，引擎内部包装为一次性 Provider 并复用拉取逻辑。
     */
    public <T> void run(String jobId,
                        FlowJoiner<T> joiner,
                        FlowSource<T> singleSource,
                        ProgressTracker tracker,
                        TemplateConfigProperties.JobConfig jc) {
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
            drainSubSource(sub, launcher, jobId);
        } catch (Exception e) {
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.PRODUCTION);
            FlowMetrics.recordError("subsource_failed", jobId);
            log.error("子流消费异常 jobId={}", jobId, e);
        }
    }

    private <T> void drainSubSource(FlowSource<T> sub, FlowLauncher<T> launcher, String jobId) {
        int itemCount = 0;
        Optional<T> item = pollNext(sub);
        while (item.isPresent()) {
            if (launcher.isStopped()) {
                launcher.getTaskOrchestrator().tracker().onProductionReleased();
                FlowMetrics.recordError("job_stopped", jobId);
                return;
            }
            launcher.launch(item.get());
            itemCount++;
            item = pollNext(sub);
        }
        if (itemCount > 0) {
            FlowMetrics.incrementCounter("subsource_items_processed", itemCount);
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
     * 推送模式：注册任务并返回 FlowInlet，业务通过 inlet.push(item) 注入数据，
     * 通过 inlet.markSourceFinished() 声明输入结束。不调用 joiner.sourceProvider()。
     */
    public <T> FlowInlet<T> startPush(String jobId,
                                      FlowJoiner<T> joiner,
                                      TemplateConfigProperties.JobConfig jobConfig) {
        return startPush(jobId, joiner, -1, jobConfig);
    }

    public <T> FlowInlet<T> startPush(String jobId,
            FlowJoiner<T> joiner,
            long total,
            TemplateConfigProperties.JobConfig jobConfig) {
        DefaultProgressTracker tracker = new DefaultProgressTracker(jobId, flowManager);
        tracker.setTotalExpected(jobId, total);
        FlowLauncher<T> launcher = flowManager.createLauncher(jobId, joiner, tracker, jobConfig);
        return new FlowInletImpl<>(launcher);
    }

    public ProgressTracker getProgressTracker(String jobId) {
        return flowManager.getActiveLauncher(jobId).getTaskOrchestrator().tracker();
    }

    public void printProgressDisplay() {
        flowManager.getFlowProgressDisplay().displayStatus(null);
    }

    /**
     * 获取框架健康状态
     *
     * @return 健康状态详情
     */
    public Map<String, Object> getHealthStatus() {
        return flowManager.getHealthStatus();
    }

    /**
     * 获取框架指标
     *
     * @return 指标映射
     */
    public Map<String, Object> getMetrics() {
        return flowManager.getMetrics();
    }

    /**
     * 输出健康检查报告到日志
     * 包含所有健康指标的详细信息
     */
    public void logHealthReport() {
        flowManager.logHealthReport();
    }
}
