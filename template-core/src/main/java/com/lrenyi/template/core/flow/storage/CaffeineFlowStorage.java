package com.lrenyi.template.core.flow.storage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.lrenyi.template.core.flow.FailureReason;
import com.lrenyi.template.core.flow.FlowJoiner;
import com.lrenyi.template.core.flow.ProgressTracker;
import com.lrenyi.template.core.flow.context.FlowEntry;
import com.lrenyi.template.core.flow.context.Orchestrator;
import com.lrenyi.template.core.flow.exception.FlowExceptionHelper;
import com.lrenyi.template.core.flow.exception.FlowPhase;
import com.lrenyi.template.core.flow.impl.FlowFinalizer;
import com.lrenyi.template.core.flow.impl.FlowLauncher;
import com.lrenyi.template.core.flow.manager.FlowManager;
import com.lrenyi.template.core.flow.metrics.FlowMetrics;
import com.lrenyi.template.core.flow.resource.FlowResourceRegistry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Caffeine 实现的 Key-Value 流式存储
 *
 * <p>适用于以下场景：</p>
 * <ul>
 *   <li>双流对齐：按 Key 匹配两个数据流中的对应项</li>
 *   <li>去重：相同 Key 只保留最新数据</li>
 *   <li>Key 检索：需要根据 Key 快速查找数据</li>
 * </ul>
 *
 * <p>特性：</p>
 * <ul>
 *   <li>支持 TTL（Time To Live）自动过期</li>
 *   <li>支持最大容量限制，超出时触发驱逐</li>
 *   <li>支持配对模式和覆盖模式</li>
 *   <li>使用单物理线程处理移除回调，避免 OOM</li>
 * </ul>
 *
 * @param <T> 存储的数据类型
 */
@Slf4j
public class CaffeineFlowStorage<T> implements FlowStorage<T> {
    @Getter
    private final FlowFinalizer<T> finalizer;
    private final Cache<String, FlowEntry<T>> cache;
    private final FlowJoiner<T> joiner;
    private final ProgressTracker progressTracker;
    private final long maxCacheSize;
    private final FlowResourceRegistry resourceRegistry;
    /** 按 key 分片的锁，保证同一 key 的配对/放入原子性，同时减少对 Caffeine 驱逐锁的并发争用 */
    private static final int STRIPE_COUNT = 256;
    private static final Lock[] KEY_STRIPES = new Lock[STRIPE_COUNT];
    static {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            KEY_STRIPES[i] = new ReentrantLock();
        }
    }

    /**
     * 构造函数
     *
     * @param maxSize               最大存储容量（背压控制），超出时触发容量驱逐
     * @param ttlMill               存活时间（毫秒），超时后触发过期驱逐并调用 onFailed
     * @param joiner                业务回调接口，用于 joinKey、onSuccess、onFailed 等
     * @param finalizer             终结器，包含 FlowResourceRegistry 引用，用于获取全局资源
     * @param progressTracker       进度跟踪器，用于记录数据流转进度
     */
    public CaffeineFlowStorage(long maxSize,
                               long ttlMill,
            FlowJoiner<T> joiner,
            FlowFinalizer<T> finalizer,
            ProgressTracker progressTracker) {
        this.joiner = joiner;
        this.finalizer = finalizer;
        this.progressTracker = progressTracker;
        this.maxCacheSize = maxSize;
        this.resourceRegistry = finalizer.resourceRegistry();
        this.cache = Caffeine.newBuilder()
                             .maximumSize(maxSize)
                             .expireAfterWrite(ttlMill, TimeUnit.MILLISECONDS)
                             .executor(resourceRegistry.getCacheRemovalExecutor())
                             .removalListener((String key, FlowEntry<T> entry, RemovalCause cause) -> {
                                 if (entry == null) {
                                     return;
                                 }
                                 onEntryRemoved(entry, cause);
                             })
                             .scheduler(Scheduler.systemScheduler())
                             .build();
    }
    
    /**
     * 将任务上下文存入存储区
     *
     * @param entry 任务上下文
     *
     * @return true 代表存入成功，需要释放生产许可；false 代表已配对处理，不需要释放生产许可
     */
    @Override
    public boolean doDeposit(FlowEntry<T> entry) {
        String key = joiner.joinKey(entry.getData());
        
        // 场景 A：不配对（覆盖模式）
        if (!joiner.needMatched()) {
            return handleOverwriteMode(key, entry);
        }
        
        // 场景 B：需要配对
        return handleMatchingMode(key, entry);
    }
    
    /**
     * 处理覆盖模式：新数据覆盖旧数据
     *
     * @param key   聚合键
     * @param entry 新数据条目
     *
     * @return true（需要释放生产许可）
     */
    private boolean handleOverwriteMode(String key, FlowEntry<T> entry) {
        FlowEntry<T> oldEntry = cache.asMap().put(key, entry);
        if (oldEntry != null) {
            // 旧数据被顶替，必须物理终结它（归还信号量、减进度）
            handleReplacedEntry(oldEntry);
        }
        // 返回 true：告知外层 entry 已经成功入库，请释放当前线程持有的信号量许可
        return true;
    }
    
    /**
     * 处理被替换的旧条目
     * 
     * @param oldEntry 被替换的旧条目
     */
    private void handleReplacedEntry(FlowEntry<T> oldEntry) {
        try (oldEntry) {
            joiner.onFailed(oldEntry.getData(), oldEntry.getJobId(), FailureReason.REPLACE);
            FlowMetrics.recordError("entry_replaced", oldEntry.getJobId());
            FlowMetrics.recordFailureReason(FailureReason.REPLACE, oldEntry.getJobId());
        } catch (Exception e) {
            FlowExceptionHelper.handleException(oldEntry.getJobId(), null, e, FlowPhase.STORAGE);
        } finally {
            progressTracker.onPassiveEgress(FailureReason.REPLACE);
        }
    }
    
    /**
     * 处理配对模式：查找匹配的条目并处理
     *
     * @param key   聚合键
     * @param entry 新数据条目
     *
     * @return true 如果没有匹配项（已存入），false 如果配对成功（已处理）
     */
    private boolean handleMatchingMode(String key, FlowEntry<T> entry) {
        FlowEntry<T> partner = findAndRemovePartner(key, entry);
        if (partner == null) {
            // 没有匹配项，已存入，返回 true 释放生产许可
            return true;
        }
        
        // 配对成功，异步处理配对逻辑
        processMatchedPair(partner, entry);
        // 返回 false：entry 已在配对处理中被终结，不需要释放生产许可
        return false;
    }
    
    /**
     * 查找并移除匹配的条目（原子：有则移除并返回旧条目，无则放入当前 entry）
     *
     * <p>使用按 key 分片锁，保证同一 key 上的操作串行化，避免大量虚拟线程同时争用
     * Caffeine 的全局驱逐锁导致 TimeoutException；语义仍为单 key 下的原子 compute。</p>
     *
     * @param key   聚合键
     * @param entry 新数据条目
     *
     * @return 匹配的旧条目，如果没有匹配则返回 null
     */
    private FlowEntry<T> findAndRemovePartner(String key, FlowEntry<T> entry) {
        Lock stripe = KEY_STRIPES[((key.hashCode() & 0x7FFFFFFF) % STRIPE_COUNT)];
        stripe.lock();
        try {
            final AtomicReference<FlowEntry<T>> matchFound = new AtomicReference<>();
            cache.asMap().compute(key, (k, existing) -> {
                if (existing != null) {
                    matchFound.set(existing);
                    return null; // 移除旧的进行配对
                }
                return entry; // 存入新条目
            });
            return matchFound.get();
        } finally {
            stripe.unlock();
        }
    }
    
    /**
     * 处理配对成功的两个条目
     *
     * @param partner 先到达的条目
     * @param entry   后到达的条目
     */
    private void processMatchedPair(FlowEntry<T> partner, FlowEntry<T> entry) {
        FlowManager flowManager = resourceRegistry.getFlowManager();
        if (flowManager == null) {
            log.warn("FlowManager not available for job {}", entry.getJobId());
            FlowMetrics.recordError("flow_manager_unavailable", entry.getJobId());
            partner.close();
            return;
        }
        
        ExecutorService globalExecutor = resourceRegistry.getGlobalExecutor();
        long matchStartTime = System.currentTimeMillis();
        globalExecutor.submit(() -> {
            try {
                executeMatchedPairLogic(partner, entry, flowManager);
                long matchLatency = System.currentTimeMillis() - matchStartTime;
                FlowMetrics.recordLatency("match_process", matchLatency);
            } catch (Exception e) {
                FlowExceptionHelper.handleException(entry.getJobId(), null, e, FlowPhase.CONSUMPTION);
                FlowMetrics.recordError("match_process_failed", entry.getJobId());
            }
        });
    }
    
    /**
     * 执行配对逻辑：获取信号量、检查匹配条件、调用回调
     *
     * @param partner     先到达的条目
     * @param entry       后到达的条目
     * @param flowManager FlowManager 实例
     */
    private void executeMatchedPairLogic(FlowEntry<T> partner, FlowEntry<T> entry, FlowManager flowManager) {
        FlowLauncher<Object> launcher = flowManager.getActiveLauncher(entry.getJobId());
        if (launcher == null) {
            log.warn("No active launcher found for job id {}", entry.getJobId());
            partner.close();
            return;
        }
        
        Orchestrator taskOrchestrator = launcher.getTaskOrchestrator();
        boolean entryAcquire = false;
        boolean partnerAcquire = false;
        
        try (partner) {
            // 获取两个信号量：entry 用 1 个，partner 用 1 个
            if (!acquirePermitsForPair(taskOrchestrator, entry, launcher)) {
                return; // 获取失败，已在方法内处理
            }
            entryAcquire = true;
            partnerAcquire = true;
            
            // 检查匹配条件并执行回调
            if (joiner.isMatched(partner.getData(), entry.getData())) {
                handleMatchedSuccess(partner, entry);
            } else {
                handleMatchedFailure(partner, entry);
            }
        } finally {
            releasePermitsAndSignal(taskOrchestrator, entryAcquire, partnerAcquire, launcher);
        }
    }
    
    /**
     * 为配对的两个条目获取信号量
     *
     * @return true 如果成功获取，false 如果失败
     */
    private boolean acquirePermitsForPair(Orchestrator taskOrchestrator,
                                          FlowEntry<T> entry,
                                          FlowLauncher<Object> launcher) {
        try {
            taskOrchestrator.acquire(); // entry 的信号量
            taskOrchestrator.acquire(); // partner 的信号量
            return true;
        } catch (InterruptedException e) {
            FlowJoiner<Object> flowJoiner = launcher.getFlowJoiner();
            taskOrchestrator.tracker().onPassiveEgress(FailureReason.REJECT);
            flowJoiner.onFailed(entry.getData(), entry.getJobId(), FailureReason.REJECT);
            FlowMetrics.recordFailureReason(FailureReason.REJECT, entry.getJobId());
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    /**
     * 处理配对成功的情况
     */
    private void handleMatchedSuccess(FlowEntry<T> partner, FlowEntry<T> entry) {
        progressTracker.onActiveEgress(); // partner 成功
        progressTracker.onActiveEgress(); // entry 成功
        try {
            joiner.onSuccess(partner.getData(), entry.getData(), entry.getJobId());
            FlowMetrics.incrementCounter("match_success");
        } catch (Exception e) {
            FlowExceptionHelper.handleException(entry.getJobId(), null, e, FlowPhase.CONSUMPTION);
            FlowMetrics.recordError("onSuccess_failed", entry.getJobId());
        }
    }
    
    /**
     * 处理配对失败的情况（不匹配）
     */
    private void handleMatchedFailure(FlowEntry<T> partner, FlowEntry<T> entry) {
        try {
            joiner.onFailed(partner.getData(), partner.getJobId(), FailureReason.MISMATCH);
            joiner.onFailed(entry.getData(), entry.getJobId(), FailureReason.MISMATCH);
            FlowMetrics.recordError("match_failed_not_matched", entry.getJobId());
            FlowMetrics.recordFailureReason(FailureReason.MISMATCH, partner.getJobId());
            FlowMetrics.recordFailureReason(FailureReason.MISMATCH, entry.getJobId());
        } catch (Exception e) {
            FlowExceptionHelper.handleException(entry.getJobId(), null, e, FlowPhase.CONSUMPTION);
        }
        progressTracker.onPassiveEgress(FailureReason.MISMATCH);
        progressTracker.onPassiveEgress(FailureReason.MISMATCH);
    }
    
    /**
     * 释放信号量并通知背压控制器
     */
    private void releasePermitsAndSignal(Orchestrator taskOrchestrator,
                                         boolean entryAcquire,
                                         boolean partnerAcquire,
                                         FlowLauncher<Object> launcher) {
        if (entryAcquire) {
            taskOrchestrator.release();
        }
        if (partnerAcquire) {
            taskOrchestrator.release();
        }
        if (launcher.getBackpressureController() != null) {
            launcher.getBackpressureController().signalRelease();
        }
    }
    
    /**
     * 显式移除指定 Key 的条目
     *
     * <p>通常用于匹配成功后主动移除条目。
     * 这会触发 removalListener，cause 为 EXPLICIT，不会执行 onEntryRemoved 的处理逻辑。</p>
     *
     * @param key 聚合键
     * @return 被移除的条目，如果不存在则返回 null
     */
    @Override
    public FlowEntry<T> remove(String key) {
        return cache.asMap().remove(key);
    }
    
    /**
     * 处理条目被移除的情况（由 Caffeine 的 removalListener 触发）
     * 
     * <p>处理流程：</p>
     * <ol>
     *   <li>在物理线程中先 acquire 全局信号量（拿不到则阻塞，最多持 1 条 entry）</li>
     *   <li>交给虚拟线程执行 body + release</li>
     * </ol>
     * 
     * @param entry 被移除的条目
     * @param cause 移除原因（超时、容量驱逐等）
     */
    private void onEntryRemoved(FlowEntry<T> entry, RemovalCause cause) {
        FlowManager flowManager = resourceRegistry.getFlowManager();
        if (flowManager == null) {
            log.warn("FlowManager not available for entry removal, jobId={}", entry.getJobId());
            FlowMetrics.recordError("flow_manager_unavailable_removal", entry.getJobId());
            return;
        }
        
        FlowLauncher<Object> launcher = flowManager.getActiveLauncher(entry.getJobId());
        try (entry) {
            // 只处理被驱逐的情况（超时或容量），其他情况（如 EXPLICIT）不需要处理
            if (!cause.wasEvicted() || launcher == null) {
                return;
            }
            
            // 记录驱逐原因
            String evictionReason = cause.name().toLowerCase();
            FlowMetrics.recordError("entry_evicted_" + evictionReason, entry.getJobId());
            
            // 在物理线程中获取信号量，然后交给虚拟线程处理
            launcher.getTaskOrchestrator().acquire();
            finalizer.submitBodyOnly(entry, launcher);
        } catch (InterruptedException ie) {
            progressTracker.onPassiveEgress(FailureReason.UNKNOWN);
            FlowExceptionHelper.handleException(entry.getJobId(), null, ie, FlowPhase.FINALIZATION);
            Thread.currentThread().interrupt();
        } finally {
            // 无论成功与否，都要通知背压控制器
            if (launcher != null && launcher.getBackpressureController() != null) {
                launcher.getBackpressureController().signalRelease();
            }
        }
    }
    
    @Override
    public long size() {
        long currentSize = cache.estimatedSize();
        // 记录缓存使用情况
        FlowMetrics.recordResourceUsage("caffeine_cache_size", currentSize);
        FlowMetrics.recordResourceUsage("caffeine_cache_max_size", maxCacheSize);
        return currentSize;
    }
    
    @Override
    public long maxCacheSize() {
        return maxCacheSize;
    }
    
    /**
     * 关闭存储，清理所有资源
     *
     * <p>会触发所有 Entry 的驱逐回调（通过 removalListener），确保资源正确释放。</p>
     */
    @Override
    public void shutdown() {
        cache.invalidateAll();
        cache.cleanUp();
        log.info("CaffeineFlowStorage shut down, all entries invalidated.");
    }
}