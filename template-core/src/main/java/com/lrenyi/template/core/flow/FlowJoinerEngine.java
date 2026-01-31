package com.lrenyi.template.core.flow;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.flow.impl.DefaultProgressTracker;
import com.lrenyi.template.core.flow.impl.FlowInletImpl;
import com.lrenyi.template.core.flow.impl.FlowLauncher;
import com.lrenyi.template.core.flow.manager.FlowManager;
import com.lrenyi.template.core.flow.source.FlowSource;
import com.lrenyi.template.core.flow.source.FlowSourceAdapters;
import com.lrenyi.template.core.flow.source.FlowSourceProvider;
import java.util.Optional;
import java.util.concurrent.Semaphore;
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
        
        FlowLauncher<T> launcher = flowManager.createLauncher(jobId, joiner, tracker, jc);
        Semaphore streamConcurrencySemaphore = launcher.getJobProducerSemaphore();
        
        try (FlowSourceProvider<T> provider = joiner.sourceProvider()) {
            runUntilNoMoreSubSources(provider, streamConcurrencySemaphore, jobId, launcher);
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
        Semaphore streamConcurrencySemaphore = launcher.getJobProducerSemaphore();
        
        try (FlowSourceProvider<T> provider = FlowSourceAdapters.singleSourceProvider(singleSource)) {
            runUntilNoMoreSubSources(provider, streamConcurrencySemaphore, jobId, launcher);
        }
    }
    
    private <T> void runUntilNoMoreSubSources(FlowSourceProvider<T> provider,
                                              Semaphore semaphore,
                                              String jobId,
                                              FlowLauncher<T> launcher) {
        while (tryRunNextSubSource(provider, semaphore, jobId, launcher)) {
            Thread.onSpinWait();
        }
    }
    
    private <T> boolean tryRunNextSubSource(FlowSourceProvider<T> provider,
                                            Semaphore semaphore,
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
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            log.warn("获取生产者端票据过程中被中断", e);
            Thread.currentThread().interrupt();
            return false;
        }
        FlowSource<T> sub = provider.nextSubSource();
        Thread.ofVirtual().name(FlowConstants.THREAD_NAME_PREFIX_PRODUCER + jobId)
              .start(() -> runSubSourceInVirtualThread(sub, launcher, semaphore, jobId));
        return true;
    }
    
    private <T> void runSubSourceInVirtualThread(FlowSource<T> sub,
                                                 FlowLauncher<T> launcher,
                                                 Semaphore semaphore,
                                                 String jobId) {
        try (sub) {
            drainSubSource(sub, launcher);
        } catch (Exception e) {
            log.error("子流消费异常 jobId={}", jobId, e);
        } finally {
            semaphore.release();
        }
    }
    
    private <T> void drainSubSource(FlowSource<T> sub, FlowLauncher<T> launcher) {
        Optional<T> item = pollNext(sub);
        while (item.isPresent()) {
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
     * 推送模式：注册任务并返回 FlowInlet，业务通过 inlet.push(item) 注入数据，
     * 通过 inlet.markSourceFinished() 声明输入结束。不调用 joiner.sourceProvider()。
     */
    public <T> FlowInlet<T> startPush(String jobId,
                                      FlowJoiner<T> joiner,
                                      TemplateConfigProperties.JobConfig jobConfig) {
        DefaultProgressTracker tracker = new DefaultProgressTracker(jobId, flowManager);
        FlowLauncher<T> launcher = flowManager.createLauncher(jobId, joiner, tracker, jobConfig);
        return new FlowInletImpl<>(launcher);
    }
    
    public ProgressTracker getProgressTracker(String jobId) {
        return flowManager.getActiveLauncher(jobId).getTaskOrchestrator().tracker();
    }
    
    public void printProgressDisplay() {
        flowManager.getFlowProgressDisplay().displayStatus(null);
    }
}