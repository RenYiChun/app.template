package com.lrenyi.template.flow.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Flow 模块执行器提供者，统一创建与管理各类执行器。
 */
public interface FlowExecutorProvider {
    
    /**
     * 获取流消费执行器（虚拟线程，消费并发由 FlowFinalizer 通过 BackpressureManager 控制）
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
    
    ExecutorService getFlowProducerExecutor();
}
