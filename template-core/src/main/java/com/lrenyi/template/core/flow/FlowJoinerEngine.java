package com.lrenyi.template.core.flow;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.flow.impl.DefaultProgressTracker;
import com.lrenyi.template.core.flow.impl.FlowLauncher;
import com.lrenyi.template.core.flow.manager.FlowManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;
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
        
        // 2. 创建发射器
        FlowLauncher<T> launcher = flowManager.createLauncher(jobId, joiner, tracker, jc);
        // 这个 semaphore 只控制同时起多少个 dealSingleData 任务
        Semaphore streamConcurrencySemaphore = launcher.getJobProducerSemaphore();
        
        try (Stream<Stream<T>> parentStream = joiner.sources()) {
            parentStream.forEach(subStream -> {
                // 阻塞主线程，直到有子流配额
                try {
                    streamConcurrencySemaphore.acquire();
                } catch (InterruptedException e) {
                    log.warn("获取生产者端票据过程中被中断", e);
                    Thread.currentThread().interrupt();
                    return;
                }
                // 启动一个虚拟线程处理这一个子流
                Thread.ofVirtual().name("prod-" + jobId).start(() -> {
                    try (subStream) {
                        // 每一个子流内部的生产速度，由 launcher 内部的“反压机制”控制
                        subStream.forEach(launcher::launch);
                    } finally {
                        streamConcurrencySemaphore.release();
                    }
                });
            });
        }
    }
    
    public ProgressTracker getProgressTracker(String jobId) {
        return flowManager.getActiveLauncher(jobId).getTaskOrchestrator().getTracker();
    }
    
    public void printProgressDisplay() {
        flowManager.getFlowProgressDisplay().displayStatus(null);
    }
}