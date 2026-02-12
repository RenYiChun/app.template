package com.lrenyi.template.core.flow.context;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import com.lrenyi.template.core.flow.internal.BackpressureController;
import com.lrenyi.template.core.flow.manager.FlowCacheManager;
import com.lrenyi.template.core.flow.manager.FlowManager;
import com.lrenyi.template.core.flow.resource.FlowResourceRegistry;
import com.lrenyi.template.core.flow.storage.FlowStorage;
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
