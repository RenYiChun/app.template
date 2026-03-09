package com.lrenyi.template.flow.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import com.lrenyi.template.flow.resource.PermitPair;

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
    
    /**
     * 按 Job 创建生产者执行器（双层信号量：先 global 再 per-job）。
     * 当 globalSemaphore 为 null 时等价于单参 createProducerExecutor(perJobSemaphore)。
     *
     * @param globalSemaphore 全局生产线程信号量（可为 null）
     * @param perJobSemaphore 每 Job 生产线程信号量
     */
    default ExecutorService createProducerExecutor(Semaphore globalSemaphore, Semaphore perJobSemaphore) {
        return createProducerExecutor(perJobSemaphore);
    }
    
    /**
     * 按 Job 创建生产者执行器（双层信号量 + 许可获取耗时指标）。
     *
     * @param globalSemaphore 全局生产线程信号量（可为 null）
     * @param perJobSemaphore 每 Job 生产线程信号量
     * @param meterRegistry  指标注册表
     * @param jobId          Job 标识
     */
    default ExecutorService createProducerExecutor(Semaphore globalSemaphore,
            Semaphore perJobSemaphore,
            io.micrometer.core.instrument.MeterRegistry meterRegistry,
            String jobId) {
        return createProducerExecutor(globalSemaphore, perJobSemaphore);
    }

    /**
     * 按 Job 创建生产者执行器，使用上下文创建好的许可对。
     * 默认抛出不支持；实现类若支持 PermitPair 需重写此方法（不暴露内部信号量引用）。
     *
     * @param permitPair    消费/生产许可对（可为 null，则退化为仅 per-job 的 createProducerExecutor）
     * @param meterRegistry 指标注册表
     * @param jobId         Job 标识
     */
    default ExecutorService createProducerExecutor(PermitPair permitPair,
            io.micrometer.core.instrument.MeterRegistry meterRegistry,
            String jobId) {
        if (permitPair == null) {
            return createProducerExecutor((Semaphore) null, (Semaphore) null);
        }
        throw new UnsupportedOperationException(
                "This provider does not override createProducerExecutor(PermitPair, ...); "
                        + "use createProducerExecutor(Semaphore, Semaphore, ...) or override this method.");
    }
}
