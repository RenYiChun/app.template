package com.lrenyi.template.flow.context;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import com.lrenyi.template.flow.internal.BackpressureController;
import com.lrenyi.template.flow.internal.FlowEgressHandler;
import com.lrenyi.template.flow.manager.FlowCacheManager;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import com.lrenyi.template.flow.resource.PermitPair;
import com.lrenyi.template.flow.storage.FlowStorage;
import lombok.Builder;
import lombok.Getter;

/**
 * 资源上下文
 * 职责：作为统一的资源上下文，封装全局资源和Job级资源
 * 包含的资源：
 * - 全局资源（通过 FlowResourceRegistry 访问）：
 * - 全局并发信号量
 * - 流消费执行器
 * - 存储出口执行器
 * - 公平锁机制
 * - 缓存管理器
 * - Job级资源：
 * - Job生产者信号量
 * - 缓存存储
 * - 背压控制器
 */
@Builder
@Getter
public class FlowResourceContext {
    /**
     * 全局资源注册表引用
     */
    private final FlowResourceRegistry resourceRegistry;
    
    /**
     * FlowManager 引用（用于Job状态查询等）
     */
    private final FlowManager flowManager;
    
    /**
     * Job级资源：Job生产者信号量
     */
    private final Semaphore jobProducerSemaphore;
    
    /**
     * Job级资源：缓存存储
     */
    private final FlowStorage<?> storage;
    
    /**
     * Job级资源：背压控制器
     */
    private final BackpressureController backpressureController;
    
    /**
     * Job级资源：生产者执行器（信号量受控）
     */
    private final ExecutorService producerExecutor;
    
    /**
     * 在途生产信号量：限制同时「已 acquire 未 release」的 launch 数，封顶 Wait(Q)，达限时 launch() 入口阻塞从而背压到上层
     */
    private final Semaphore inFlightProductionSemaphore;
    
    /**
     * 每 Job 消费并发信号量：限制该 Job 同时持有的消费许可数
     */
    private final Semaphore jobConsumerSemaphore;

    /**
     * 每 Job 等待消费许可槽位：提交 finalizer 前 acquire，任务完成时 release，严格将「已离库未终结」条数限制在 limit 内。
     * 为 null 表示未配置上限（effectivePendingConsumer<=0）。
     */
    private final Semaphore pendingConsumerSlotSemaphore;

    /**
     * 统一出口记账：供 FlowLauncher 在 stopped 等场景调用 performSingleConsumed
     */
    private final FlowEgressHandler<?> egressHandler;

    /**
     * 消费许可对（global + per-job），创建时绑定，各处引用同一实例。
     */
    private final PermitPair consumerPermitPair;

    /**
     * 在途生产许可对；为 null 表示未启用在途限制。
     */
    private final PermitPair inFlightPermitPair;

    /**
     * 生产线程许可对（全局+每 Job）；为 null 表示仅用 per-job 信号量。
     */
    private final PermitPair producerPermitPair;

    // ========== 全局资源访问便捷方法 ==========
    
    /**
     * 获取全局并发信号量
     */
    public Semaphore getGlobalSemaphore() {
        return resourceRegistry.getGlobalSemaphore();
    }
    
    /**
     * 获取流消费执行器
     */
    public ExecutorService getFlowConsumerExecutor() {
        return resourceRegistry.getFlowConsumerExecutor();
    }
    
    /**
     * 获取存储出口执行器
     */
    public ScheduledExecutorService getStorageEgressExecutor() {
        return resourceRegistry.getStorageEgressExecutor();
    }
    
    /**
     * 获取公平锁
     */
    public Lock getFairLock() {
        return resourceRegistry.getFairLock();
    }
    
    /**
     * 获取许可释放条件变量
     */
    public Condition getPermitReleased() {
        return resourceRegistry.getPermitReleased();
    }
    
    /**
     * 获取缓存管理器
     */
    public FlowCacheManager getCacheManager() {
        return resourceRegistry.getCacheManager();
    }

    /**
     * 获取等待消费槽位信号量；提交 finalizer 前 acquire(1)，任务完成时 release(1)，保证 pending 不超过 limit。
     */
    public Semaphore getPendingConsumerSlotSemaphore() {
        return pendingConsumerSlotSemaphore;
    }
}
