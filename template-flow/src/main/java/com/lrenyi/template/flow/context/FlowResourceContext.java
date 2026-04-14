package com.lrenyi.template.flow.context;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import com.lrenyi.template.flow.backpressure.BackpressureManager;
import com.lrenyi.template.flow.internal.EgressConsumeStrategy;
import com.lrenyi.template.flow.internal.FlowEgressHandler;
import com.lrenyi.template.flow.internal.FlowFinalizer;
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
     * Job级资源：缓存存储
     */
    private final FlowStorage<?> storage;

    /**
     * Job级资源：背压管理器
     */
    private final BackpressureManager backpressureManager;

    /**
     * Job级资源：生产者执行器（信号量受控）
     */
    private final ExecutorService producerExecutor;

    /**
     * 统一出口记账：供 FlowLauncher 在 stopped 等场景调用 performSingleConsumed
     */
    private final FlowEgressHandler<?> egressHandler;

    /**
     * FlowFinalizer：供背压超时时当前线程直接调用 submitDataToConsumer，数据不丢
     */
    private final FlowFinalizer<?> finalizer;

    /**
     * Storage egress 后进入消费逻辑的执行策略。
     */
    private final EgressConsumeStrategy<?> egressConsumeStrategy;

    /**
     * 消费许可对（global + per-job），创建时绑定，各处引用同一实例。
     */
    private final PermitPair consumerPermitPair;

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
}
