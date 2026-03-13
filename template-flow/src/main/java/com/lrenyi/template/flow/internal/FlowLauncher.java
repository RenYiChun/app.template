package com.lrenyi.template.flow.internal;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.backpressure.BackpressureManager;
import com.lrenyi.template.flow.backpressure.DimensionLease;
import com.lrenyi.template.flow.backpressure.dimension.InFlightProductionDimension;
import com.lrenyi.template.flow.backpressure.dimension.ProducerConcurrencyDimension;
import com.lrenyi.template.flow.backpressure.dimension.StorageDimension;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.context.FlowResourceContext;
import com.lrenyi.template.flow.exception.FlowExceptionHelper;
import com.lrenyi.template.flow.exception.FlowPhase;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.model.FlowStorageType;
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
    private final Semaphore jobProducerSemaphore;
    private final TemplateConfigProperties.Flow flow;
    private final BackpressureManager backpressureManager;
    private final FlowResourceContext resourceContext;
    private final MatchRetryCoordinator<T> matchRetryCoordinator;
    private final ProgressTracker tracker;
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
        this.jobProducerSemaphore = resourceContext.getJobProducerSemaphore();
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

    /** 用于监控指标标签的 jobId（可读展示名） */
    public String getMetricJobId() {
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
                   .tag(FlowMetricNames.TAG_ERROR_TYPE, "job_stopped")
                   .tag(FlowMetricNames.TAG_PHASE, PHASE_PRODUCTION)
                   .register(registry())
                   .increment();
            return;
        }

        DimensionLease inFlightLease;
        try {
            inFlightLease = backpressureManager.acquire(InFlightProductionDimension.ID, () -> stopped);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.PRODUCTION, "inFlight_acquire_interrupted",
                    metricJobId);
            consumeOnBackpressureTimeout(data);
            return;
        } catch (TimeoutException e) {
            FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.PRODUCTION, "inFlight_acquire_timeout",
                    metricJobId);
            consumeOnBackpressureTimeout(data);
            return;
        }
        tracker.onProductionAcquired();

        if (stopped) {
            tracker.onProductionReleased();
            inFlightLease.close();
            Counter.builder(FlowMetricNames.ERRORS)
                   .tag(FlowMetricNames.TAG_ERROR_TYPE, "job_stopped")
                   .tag(FlowMetricNames.TAG_PHASE, PHASE_PRODUCTION)
                   .register(registry())
                   .increment();
            return;
        }

        submitDepositTask(data, tracker, inFlightLease);
    }

    private MeterRegistry registry() {
        return flowManager.getMeterRegistry();
    }

    private void submitDepositTask(T data, ProgressTracker tracker, DimensionLease inFlightLease) {
        // 在调用线程（生产方）申请生产并发席位，直接对调用方形成背压；
        // 任务提交后在虚拟线程 finally 中释放，控制同时执行 deposit 逻辑的并发数。
        DimensionLease producerLease;
        try {
            producerLease = backpressureManager.acquire(ProducerConcurrencyDimension.ID, () -> stopped);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            FlowExceptionHelper.handleException(jobId,
                                                null,
                                                e,
                                                FlowPhase.STORAGE,
                                                "producer_concurrency_acquire_interrupted"
            );
            tracker.onProductionReleased();
            inFlightLease.close();
            consumeOnBackpressureTimeout(data);
            return;
        } catch (TimeoutException e) {
            FlowExceptionHelper.handleException(jobId,
                                                null,
                                                e,
                                                FlowPhase.STORAGE,
                                                "producer_concurrency_acquire_timeout",
                                                metricJobId
            );
            tracker.onProductionReleased();
            inFlightLease.close();
            consumeOnBackpressureTimeout(data);
            return;
        }
        FlowLauncher<?> launcher = this;
        getProducerExecutor().execute(() -> {
            try (FlowEntry<T> ctx = new FlowEntry<>(data, jobId)) {
                matchRetryCoordinator.initRetryRemainingIfNecessary(ctx);
                if (stopped) {
                    log.info("Deposit task skipped because job already stopped, {}",
                            FlowLogHelper.formatJobContext(jobId, metricJobId));
                    @SuppressWarnings("unchecked") var handler =
                        (FlowEgressHandler<T>) resourceContext.getEgressHandler();
                    handler.performSingleConsumed(ctx, EgressReason.SHUTDOWN);
                    tracker.onTerminated(1);
                    return;
                }

                long depositStartTime = System.currentTimeMillis();
                DimensionLease storageLease;
                try {
                    storageLease = backpressureManager.acquire(StorageDimension.ID, () -> stopped);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    FlowExceptionHelper.handleException(jobId,
                                                        null,
                                                        e,
                                                        FlowPhase.STORAGE,
                                                        "storage_acquire_interrupted",
                                                        metricJobId
                    );
                    getFinalizer().submitDataToConsumer(ctx, launcher, EgressReason.BACKPRESSURE_TIMEOUT);
                    return;
                } catch (TimeoutException e) {
                    FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.STORAGE, "storage_acquire_timeout",
                            metricJobId);
                    getFinalizer().submitDataToConsumer(ctx, launcher, EgressReason.BACKPRESSURE_TIMEOUT);
                    return;  // finally 会执行 onProductionReleased、inFlightLease.close、producerLease.close
                }
                ctx.setStorageLease(storageLease);
                try {
                    boolean deposited = getStorage().deposit(ctx);
                    if (!deposited) {
                        // 未入槽（已在 deposit 内部消费或无需存储），幂等关闭租约
                        ctx.closeStorageLease();
                    }
                } catch (Throwable t) {
                    ctx.closeStorageLease();
                    throw t;
                }
                long depositLatency = System.currentTimeMillis() - depositStartTime;

                Timer.builder(FlowMetricNames.DEPOSIT_DURATION)
                     .tag(FlowMetricNames.TAG_JOB_ID, metricJobId)
                     .register(registry())
                     .record(depositLatency, TimeUnit.MILLISECONDS);
            } catch (Throwable e) {
                log.error("Deposit task failed, {}", FlowLogHelper.formatJobContext(jobId, metricJobId), e);
                FlowExceptionHelper.handleException(jobId, null, e, FlowPhase.STORAGE, "deposit_failed", metricJobId);
            } finally {
                tracker.onProductionReleased();
                inFlightLease.close();
                producerLease.close();
                if (tracker.isProductionComplete() && !flowJoiner.needMatched()) {
                    storage.triggerCompletionDrain();
                }
            }
        });
    }

    /**
     * 背压超时时，当前线程直接调用 submitDataToConsumer 消费数据，避免丢数。
     * entry 生命周期由 submitDataToConsumer 内部的 consumer 任务负责。
     */
    @SuppressWarnings("unchecked")
    private void consumeOnBackpressureTimeout(T data) {
        FlowFinalizer<?> fin = resourceContext.getFinalizer();
        if (fin == null) {
            return;
        }
        FlowEntry<T> entry = new FlowEntry<>(data, jobId);
        ((FlowFinalizer<T>) fin).submitDataToConsumer(entry, this, EgressReason.BACKPRESSURE_TIMEOUT);
    }

    @SuppressWarnings("unchecked")
    private FlowFinalizer<T> getFinalizer() {
        return (FlowFinalizer<T>) resourceContext.getFinalizer();
    }

    public long getCacheCapacity() {
        return flow.getLimits().getPerJob().getStorageCapacity();
    }

    public ExecutorService getProducerExecutor() {
        return resourceContext.getProducerExecutor();
    }

    public boolean isCompleted() {
        return tracker.isCompleted();
    }

    public void stop(boolean force) {
        if (stopped) {
            return;
        }
        log.info("停止 Job [{}], force={}", FlowLogHelper.formatJobContext(jobId, metricJobId), force);
        this.stopped = true;
        tracker.markSourceFinished(jobId);
        try {
            FlowStorageType type = flowJoiner.getStorageType();
            resourceContext.getCacheManager()
               .invalidateByJobId(jobId, metricJobId, type, flowJoiner.getDataType().getSimpleName());
        } catch (Exception e) {
            log.error("Job [{}] 停止时清理 Storage 失败", FlowLogHelper.formatJobContext(jobId, metricJobId), e);
        }
        flowManager.unregister(jobId);
    }
}
