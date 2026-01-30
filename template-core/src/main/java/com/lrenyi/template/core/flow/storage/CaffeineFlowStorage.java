package com.lrenyi.template.core.flow.storage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.lrenyi.template.core.flow.FlowJoiner;
import com.lrenyi.template.core.flow.ProgressTracker;
import com.lrenyi.template.core.flow.context.FlowEntry;
import com.lrenyi.template.core.flow.context.Orchestrator;
import com.lrenyi.template.core.flow.impl.FlowFinalizer;
import com.lrenyi.template.core.flow.impl.FlowLauncher;
import com.lrenyi.template.core.flow.manager.FlowManager;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Caffeine 实现的 Key-Value 流式存储
 * 适用于：双流对齐、去重、以及需要根据 Key 检索的匹配场景
 */
@Slf4j
public class CaffeineFlowStorage<T> implements FlowStorage<T> {
    @Getter
    private final FlowFinalizer<T> finalizer;
    private final Cache<String, FlowEntry<T>> cache;
    private final FlowJoiner<T> joiner;
    private final ProgressTracker progressTracker;
    private final long maxCacheSize;
    
    /**
     * @param maxSize               最大存储容量（背压控制）
     * @param ttlMill            存活时间（超时触发 onFailed）
     * @param joiner                业务回调接口
     * @param storageEgressExecutor 所有 store 共用的「从存储取数」单物理线程，由 FlowManager.getStorageEgressExecutor() 提供
     */
    public CaffeineFlowStorage(long maxSize,
                               long ttlMill,
                               FlowJoiner<T> joiner,
                               FlowFinalizer<T> finalizer,
                               ProgressTracker progressTracker,
                               Executor storageEgressExecutor) {
        this.joiner = joiner;
        this.finalizer = finalizer;
        this.progressTracker = progressTracker;
        this.maxCacheSize = maxSize;
        this.cache = Caffeine.newBuilder()
                             .maximumSize(maxSize)
                             .expireAfterWrite(ttlMill, TimeUnit.MILLISECONDS)
                             .executor(storageEgressExecutor)
                             .removalListener((String key, FlowEntry<T> entry, RemovalCause cause) -> {
                                 if (entry == null) {
                                     return;
                                 }
                                 onEntryRemoved(entry, cause);
                             })
                             .scheduler(Scheduler.systemScheduler())
                             .build();
    }
    
    @Override
    public boolean doDeposit(FlowEntry<T> entry) {
        String key = joiner.joinKey(entry.getData());
        // --- 场景 A：不配对 (覆盖模式) ---
        if (!joiner.needMatched()) {
            // 使用 put 覆盖并拿回旧值
            FlowEntry<T> oldEntry = cache.asMap().put(key, entry);
            if (oldEntry != null) {
                // 旧数据被顶替了，必须物理终结它（归还信号量、减进度）
                try (oldEntry) {
                    joiner.onFailed(oldEntry.getData(), oldEntry.getJobId());
                } finally {
                    progressTracker.onPassiveEgress();
                }
            }
            // 返回 true：告知外层 entry 已经成功入库，请释放当前线程持有的信号量许可（归还门票）
            return true;
        }
        // --- 场景 B：需要配对 ---
        final AtomicReference<FlowEntry<T>> matchFound = new AtomicReference<>();
        cache.asMap().compute(key, (k, existing) -> {
            if (existing != null) {
                matchFound.set(existing);
                return null; // 移除旧的进行配对
            }
            return entry; // 存入自己
        });
        FlowEntry<T> partner = matchFound.get();
        if (partner == null) {
            return true;
        }
        // 配对成功：当前线程同时拿到了 partner 和 entry。
        // 我们在当前线程彻底完成它们的使命。
        FlowManager flowManager = finalizer.flowManager();
        ExecutorService globalExecutor = flowManager.getGlobalExecutor();
        
        globalExecutor.submit(() -> {
            FlowLauncher<Object> launcher = flowManager.getActiveLauncher(entry.getJobId());
            if (launcher == null) {
                log.warn("No active launcher found for job id {}", entry.getJobId());
                partner.close();
                return;
            }
            Orchestrator<Object> taskOrchestrator = launcher.getTaskOrchestrator();
            boolean entryAcquire = false;
            boolean parentAcquire = false;
            try (partner) {
                try {
                    //这里要增加两个信号量，entry用1个，partner用1个
                    taskOrchestrator.acquire();
                    entryAcquire = true;
                    taskOrchestrator.acquire();
                    parentAcquire = true;
                } catch (InterruptedException e) {
                    FlowJoiner<Object> flowJoiner = launcher.getFlowJoiner();
                    taskOrchestrator.getTracker().onPassiveEgress();
                    flowJoiner.onFailed(entry.getData(), entry.getJobId());
                    Thread.currentThread().interrupt();
                }
                if (joiner.isMatched(partner.getData(), entry.getData())) {
                    progressTracker.onActiveEgress();
                    progressTracker.onActiveEgress();
                    joiner.onSuccess(partner.getData(), entry.getData(), entry.getJobId());
                } else {
                    joiner.onFailed(partner.getData(), partner.getJobId());
                    progressTracker.onPassiveEgress();
                    joiner.onFailed(entry.getData(), entry.getJobId());
                    progressTracker.onPassiveEgress();
                }
            } finally {
                if (entryAcquire) {
                    taskOrchestrator.release();
                }
                if (parentAcquire) {
                    taskOrchestrator.release();
                }
                if (launcher.getBackpressureController() != null) {
                    launcher.getBackpressureController().signalRelease();
                }
            }
        });
        // 重要：返回 false！
        // 因为 entry 已经在上面的 try-with-resources 里被标记为彻底终结（refCnt 归零）。
        // 此时不需要外层再调用 releasePermitOnly。
        return false;
    }
    
    @Override
    public FlowEntry<T> remove(String key) {
        // 显式移除（通常是匹配成功时调用）
        // 这会触发 removalListener，cause 为 EXPLICIT
        return cache.asMap().remove(key);
    }
    
    private void onEntryRemoved(FlowEntry<T> entry, RemovalCause cause) {
        // 情况 A：自然失效（超时驱逐或容量驱逐）
        // 在物理线程中先 acquire 全局信号量（拿不到则阻塞，最多持 1 条 entry），再交给虚拟线程跑 body + release
        FlowManager flowManager = finalizer.flowManager();
        FlowLauncher<Object> launcher = flowManager.getActiveLauncher(entry.getJobId());
        try (entry) {
            if (!cause.wasEvicted() || launcher == null) {
                return;
            }
            launcher.getTaskOrchestrator().acquire();
            finalizer.submitBodyOnly(entry, launcher);
        } catch (InterruptedException ie) {
            progressTracker.onPassiveEgress();
            Thread.currentThread().interrupt();
        } finally {
            if (launcher != null && launcher.getBackpressureController() != null) {
                launcher.getBackpressureController().signalRelease();
            }
        }
    }
    
    @Override
    public long size() {
        return cache.estimatedSize();
    }
    
    @Override
    public long maxCacheSize() {
        return maxCacheSize;
    }
    
    @Override
    public void shutdown() {
        // 触发所有 Entry 的驱逐回调，确保资源释放
        cache.invalidateAll();
        cache.cleanUp();
        log.info("CaffeineFlowStorage shut down, all entries invalidated.");
    }
}