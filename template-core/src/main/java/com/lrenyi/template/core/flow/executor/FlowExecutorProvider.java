package com.lrenyi.template.core.flow.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;

/**
 * Flow 模块执行器提供者，统一创建与管理各类执行器。
 */
public interface FlowExecutorProvider {
    
    /**
     * 获取流消费执行器（虚拟线程，消费者仍通过 Orchestrator.acquire 控制并发）
     */
    ExecutorService getFlowConsumerExecutor();
    
    /**
     * 获取存储出口调度执行器
     */
    ScheduledExecutorService getStorageEgressExecutor();
    
    /**
     * 获取 Caffeine 驱逐回调执行器
     */
    ExecutorService getCacheRemovalExecutor();
    
    /**
     * 按 Job 创建生产者执行器（信号量受控）。
     * 每 Job 调用一次，返回独立的 BoundedVirtualExecutor，Job 结束时应 shutdown。
     *
     * @param semaphore Job 级生产者信号量
     */
    ExecutorService createProducerExecutor(Semaphore semaphore);
}
