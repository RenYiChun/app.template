package com.lrenyi.template.flow.internal;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.backpressure.BackpressureManager;
import com.lrenyi.template.flow.backpressure.DimensionLease;
import com.lrenyi.template.flow.backpressure.dimension.StorageDimension;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.context.FlowResourceContext;
import com.lrenyi.template.flow.exception.FlowExceptionContext;
import com.lrenyi.template.flow.exception.FlowExceptionHelper;
import com.lrenyi.template.flow.exception.FlowPhase;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.metrics.FlowMetricTags;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.storage.FlowStorage;
import com.lrenyi.template.flow.util.FlowLogHelper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * 流量发射器 - 动态配置版
 */
@Slf4j
@Getter
@Setter
public class FlowLauncher<T> {
    private static final String PHASE_PRODUCTION = "PRODUCTION";
    private final AtomicInteger counter = new AtomicInteger(0);
    private final String jobId;
    private final String metricJobId;
    private final FlowStorage<T> storage;
    private final FlowManager flowManager;
    private final FlowJoiner<T> flowJoiner;
    private final TemplateConfigProperties.Flow flow;
    private final BackpressureManager backpressureManager;
    private final FlowResourceContext resourceContext;
    private final MatchRetryCoordinator<T> matchRetryCoordinator;
    private final ProgressTracker tracker;
    private final AtomicBoolean runtimeReleased = new AtomicBoolean(false);
    private volatile boolean stopped = false;
    /** 推送模式下 in-flight push 计数，由 FlowInletImpl 注册，用于完成判定（未注册时视为 0）。
     * -- SETTER --
     *  推送模式下由引擎注册，用于完成判定时要求 inFlightPush==0 再 unregister，避免 Executor 已关闭。
     */
    private volatile IntSupplier inFlightPushCountSupplier;

    @SuppressWarnings("unchecked")
    private FlowLauncher(String jobId,
        String metricJobId,
        FlowManager flowManager,
        FlowJoiner<T> flowJoiner,
        ProgressTracker tracker,
        TemplateConfigProperties.Flow flow,
        FlowResourceContext resourceContext) {
        this.jobId = jobId;
        this.metricJobId = metricJobId != null ? metricJobId : jobId;
        this.flowManager = flowManager;
        this.flowJoiner = flowJoiner;
        this.resourceContext = resourceContext;
        this.flow = flow;
        this.tracker = tracker;
        this.storage = (FlowStorage<T>) resourceContext.getStorage();
        this.backpressureManager = resourceContext.getBackpressureManager();
        this.matchRetryCoordinator = new MatchRetryCoordinator<>(jobId,
                                                                 flow.getLimits().getPerJob(),
                                                                 flowJoiner,
                                                                 flowManager
        );
    }

    public static <T> FlowLauncher<T> create(String jobId,
        String metricJobId,
        FlowJoiner<T> flowJoiner,
        FlowManager flowManager,
        ProgressTracker tracker,
        TemplateConfigProperties.Flow flow,
        FlowResourceContext resourceContext) {
        return new FlowLauncher<>(jobId, metricJobId, flowManager, flowJoiner, tracker, flow, resourceContext);
    }

    /** 用于监控指标标签的 jobId（与 {@link ProgressTracker#getMetricJobId()} 对齐）。 */
    public String getMetricJobId() {
        String t = tracker.getMetricJobId();
        if (t != null && !t.isEmpty()) {
            return t;
        }
        return metricJobId;
    }

    /** 当前 in-flight push 数（未注册 supplier 时返回 0）。 */
    public int getInFlightPushCount() {
        IntSupplier s = inFlightPushCountSupplier;
        return s != null ? s.getAsInt() : 0;
    }

    public void launch(T data) {
        if (stopped) {
            Counter.builder(FlowMetricNames.ERRORS)
                   .tags(metricTags().toTags())
                   .tag(FlowMetricNames.TAG_ERROR_TYPE, "job_stopped")
                   .tag(FlowMetricNames.TAG_PHASE, PHASE_PRODUCTION)
                   .register(registry())
                   .increment();
            return;
        }

        tracker.onProductionAcquired();

        if (stopped) {
            tracker.onProductionReleased();
            Counter.builder(FlowMetricNames.ERRORS)
                   .tags(metricTags().toTags())
                   .tag(FlowMetricNames.TAG_ERROR_TYPE, "job_stopped")
                   .tag(FlowMetricNames.TAG_PHASE, PHASE_PRODUCTION)
                   .register(registry())
                   .increment();
            return;
        }

        depositNow(data);
    }

    private MeterRegistry registry() {
        return flowManager.getMeterRegistry();
    }

    private FlowMetricTags metricTags() {
        return FlowMetricTags.resolve(jobId, getMetricJobId(), tracker.getStageDisplayName());
    }

    private void depositNow(T data) {
        FlowEntry<T> ctx = new FlowEntry<>(data, jobId);
        try {
            matchRetryCoordinator.initRetryRemainingIfNecessary(ctx);
            if (stopped) {
                log.info("Deposit skipped because job already stopped, {}",
                        FlowLogHelper.formatJobContext(jobId, getMetricJobId()));
                @SuppressWarnings("unchecked") var handler =
                    (FlowEgressHandler<T>) resourceContext.getEgressHandler();
                try {
                    handler.performSingleConsumed(ctx, EgressReason.SHUTDOWN);
                } finally {
                    ctx.close();
                }
                ctx = null;
                tracker.onTerminated(1);
                return;
            }
            long depositStartTime = System.currentTimeMillis();
            DimensionLease storageLease;
            try {
                storageLease = backpressureManager.acquire(StorageDimension.ID, () -> stopped);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (stopped) {
                    FlowExceptionContext context = new FlowExceptionContext(jobId,
                            null,
                            e,
                            FlowPhase.STORAGE,
                            "storage_acquire_interrupted");
                    context.addContext("displayName", getMetricJobId());
                    context.addContext("expectedInterruption", true);
                    FlowExceptionHelper.handleException(context);
                } else {
                    FlowExceptionHelper.handleException(jobId,
                                                        null,
                                                        e,
                                                        FlowPhase.STORAGE,
                                                        "storage_acquire_interrupted",
                                                        getMetricJobId()
                    );
                }
                getEgressConsumeStrategy().submitSingle(ctx, this, EgressReason.BACKPRESSURE_TIMEOUT);
                ctx = null;
                return;
            } catch (TimeoutException e) {
                FlowExceptionHelper.handleException(jobId,
                                                    null,
                                                    e,
                                                    FlowPhase.STORAGE,
                                                    "storage_acquire_timeout",
                                                    getMetricJobId()
                );
                getEgressConsumeStrategy().submitSingle(ctx, this, EgressReason.BACKPRESSURE_TIMEOUT);
                ctx = null;
                return;
            }
            ctx.setStorageLease(storageLease);
            try {
                boolean deposited = getStorage().deposit(ctx);
                if (!deposited) {
                    ctx.closeStorageLease();
                }
            } catch (Throwable t) {
                ctx.closeStorageLease();
                throw t;
            }
            long depositLatency = System.currentTimeMillis() - depositStartTime;

            Timer.builder(FlowMetricNames.DEPOSIT_DURATION)
                 .tags(metricTags().toTags())
                 .register(registry())
                 .record(depositLatency, TimeUnit.MILLISECONDS);
        } catch (Throwable e) {
            log.error("Deposit failed, {}", FlowLogHelper.formatJobContext(jobId, getMetricJobId()), e);
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.STORAGE, "deposit_failed", getMetricJobId());
        } finally {
            if (ctx != null) {
                ctx.close();
            }
            tracker.onProductionReleased();
            if (tracker.isProductionComplete() && !flowJoiner.needMatched()) {
                storage.triggerCompletionDrain();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private EgressConsumeStrategy<T> getEgressConsumeStrategy() {
        return (EgressConsumeStrategy<T>) resourceContext.getEgressConsumeStrategy();
    }

    public long getCacheCapacity() {
        return flow.getLimits().getPerJob().getStorageCapacity();
    }

    public ExecutorService getProducerExecutor() {
        return resourceContext.getProducerExecutor();
    }

    public boolean isCompleted() {
        return tracker.isCompleted(false);
    }

    public boolean releaseRuntimeResources() {
        if (!runtimeReleased.compareAndSet(false, true)) {
            return false;
        }
        try {
            resourceContext.getCacheManager().invalidateForJoiner(jobId, getMetricJobId(), flowJoiner);
        } catch (Exception e) {
            log.error("Job [{}] 释放运行期资源时清理 Storage 失败",
                    FlowLogHelper.formatJobContext(jobId, getMetricJobId()),
                    e);
        }
        try {
            resourceContext.getResourceRegistry().deregisterJob(jobId);
        } catch (Exception e) {
            log.error("Job [{}] 释放运行期资源时注销 FairShare 失败",
                    FlowLogHelper.formatJobContext(jobId, getMetricJobId()),
                    e);
        }
        ExecutorService producerExecutor = resourceContext.getProducerExecutor();
        if (producerExecutor != null && !producerExecutor.isShutdown()) {
            producerExecutor.shutdown();
        }
        return true;
    }

    public void stop(boolean force) {
        if (!stopped) {
            log.info("停止 Job [{}], force={}", FlowLogHelper.formatJobContext(jobId, getMetricJobId()), force);
            this.stopped = true;
            tracker.markSourceFinished(jobId, true);
            releaseRuntimeResources();
        }
        if (force) {
            flowManager.unregister(jobId);
        } else {
            flowManager.scheduleUnregister(jobId);
        }
    }
}
