package com.lrenyi.template.core.flow.impl;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.flow.FlowJoiner;
import com.lrenyi.template.core.flow.ProgressTracker;
import com.lrenyi.template.core.flow.config.FlowStorageType;
import com.lrenyi.template.core.flow.context.FlowEntry;
import com.lrenyi.template.core.flow.context.FlowResourceContext;
import com.lrenyi.template.core.flow.context.Orchestrator;
import com.lrenyi.template.core.flow.context.Registration;
import com.lrenyi.template.core.flow.manager.FlowManager;
import com.lrenyi.template.core.flow.storage.FlowStorage;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * 流量发射器 - 动态配置版
 * 职责：使用全局虚拟线程池，通过逻辑开关控制 Job 生命周期，实现精准限流与缓存托管
 */
@Slf4j
@Getter
@Setter
public class FlowLauncher<T> {
    private final AtomicInteger counter = new AtomicInteger(0);
    private final String jobId;
    private final Orchestrator taskOrchestrator;
    private final FlowStorage<T> storage;
    private final FlowManager flowManager;
    private final FlowJoiner<T> flowJoiner;
    private volatile boolean stopped = false;
    private final Semaphore jobProducerSemaphore;
    private final TemplateConfigProperties.JobConfig jobConfig;
    private final BackpressureController backpressureController;
    private final FlowResourceContext resourceContext;
    
    private FlowLauncher(String jobId,
                         FlowManager flowManager,
                         FlowJoiner<T> flowJoiner,
                         ProgressTracker tracker, Registration registration, FlowResourceContext resourceContext) {
        this.jobId = jobId;
        this.flowManager = flowManager;
        this.flowJoiner = flowJoiner;
        this.resourceContext = resourceContext;
        this.jobConfig = registration.getJobConfig();
        this.jobProducerSemaphore = resourceContext.getJobProducerSemaphore();
        this.storage = (FlowStorage<T>) resourceContext.getStorage();
        this.backpressureController = resourceContext.getBackpressureController();
        this.taskOrchestrator = new Orchestrator(jobId, tracker, registration, resourceContext);
    }
    
    public static <T> FlowLauncher<T> create(String jobId,
                                             FlowJoiner<T> flowJoiner,
                                             FlowManager flowManager,
                                             ProgressTracker tracker,
                                             Registration registration,
                                             FlowResourceContext resourceContext) {
        
        return new FlowLauncher<>(jobId, flowManager, flowJoiner, tracker, registration, resourceContext);
    }
    
    public void launch(T data) {
        ProgressTracker tracker = taskOrchestrator.tracker();
        tracker.onProductionAcquired();
        if (stopped) {
            log.warn("the job is stop for jobId: {}", jobId);
            tracker.onProductionReleased();
            return;
        }
        try {
            awaitBackpressure();
        } catch (InterruptedException e) {
            tracker.onProductionReleased();
            return;
        }
        resourceContext.getGlobalExecutor().submit(() -> {
            try (FlowEntry<T> ctx = new FlowEntry<>(data, jobId)) {
                if (stopped) {
                    log.warn("the job is stop for jobId: {}", jobId);
                    tracker.onPassiveEgress();
                    flowJoiner.onFailed(data, jobId);
                    return;
                }
                storage.deposit(ctx);
            } catch (Throwable e) {
                log.error("Job [{}] 运行时异常: {}", jobId, e.getMessage(), e);
            } finally {
                tracker.onProductionReleased();
            }
        });
    }
    
    private void awaitBackpressure() throws InterruptedException {
        // 1. 快速失败：如果任务已经停止，直接抛出异常或返回
        if (stopped) {
            return;
        }
        // 这里的 backpressureController 就是你刚才写的那个类
        try {
            backpressureController.awaitSpace(() -> stopped);
        } catch (InterruptedException e) {
            // 虚拟线程被中断，通常意味着整个线程池或 Job 正在关闭
            Thread.currentThread().interrupt();
            throw e;
        }
    }
    
    public long getCacheCapacity() {
        return jobConfig.getMaxCacheSize();
    }
    
    /**
     * 停止当前 Job
     *
     * @param force true: 强制清理缓存资源；false: 优雅停止，不再进新数据
     */
    public void stop(boolean force) {
        if (stopped) {
            return;
        }
        log.info("停止 Job [{}], force={}", jobId, force);
        this.stopped = true; // 开启拦截闸门
        // 告诉 Tracker：生产端已关闭。
        // 这样当 activeConsumers 归零时，getCompletionFuture() 就会完成。
        taskOrchestrator.tracker().markSourceFinished(jobId);
        // 强制模式：按 jobId+storageType 失效并 shutdown，与 getOrCreateStorage 的 key 一致
        if (force) {
            try {
                FlowStorageType type = flowJoiner.getStorageType();
                resourceContext.getCacheManager()
                               .invalidateByJobId(jobId, type, flowJoiner.getDataType().getSimpleName());
            } catch (Exception e) {
                log.error("Job [{}] 强制停止清理失败", jobId, e);
            }
        }
        flowManager.unregister(jobId);
    }
}