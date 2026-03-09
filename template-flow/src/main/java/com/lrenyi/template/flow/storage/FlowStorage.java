package com.lrenyi.template.flow.storage;

import com.lrenyi.template.flow.context.FlowEntry;

/**
 * 任务存储抽象接口
 * 职责：负责 FlowEntry 的暂存、检索及生命周期维护。
 * <p>
 * 若实现类需要「从存储取出数据」时在单物理线程中 acquire 再交给虚拟线程处理（防 OOM），
 * 应使用 FlowManager.getStorageEgressExecutor() 作为该离场路径的 executor。
 */
public interface FlowStorage<T> {
    
    /**
     * 将任务上下文存入存储区
     *
     * @param entry 任务上下文
     * @return true 表示入库成功；false 表示拒绝（如 Queue 满）
     */
    default boolean deposit(FlowEntry<T> entry) {
        if (entry == null) {
            return true;
        }
        // 1. 物理引用 +1 (确保在 Storage 期间对象不被销毁)
        entry.retain();
        // 2. 调用具体的物理存储逻辑 (由子类实现)
        boolean success = doDeposit(entry);
        if (!success) {
            entry.release();
        }
        return success;
    }
    
    /**
     * 将任务上下文存入存储区
     *
     * @param ctx 任务上下文
     * @return true 代表存入成功；
     *     false 代表存储已满或拒绝准入，此时框架应触发 onRejected 回调
     */
    boolean doDeposit(FlowEntry<T> ctx);
    
    long size();
    
    long maxCacheSize();

    /**
     * 当前已使用的 entry 数量。支持受控超时实现按 entry 计数。
     * 默认回退到 size()，以兼容旧实现。
     */
    default long usedEntries() {
        return size();
    }

    /**
     * entry 容量上限。默认回退到 maxCacheSize()。
     */
    default long entryLimit() {
        return maxCacheSize();
    }

    /**
     * 是否支持延迟驱逐（受控超时 + 下游压力协同）。
     */
    default boolean supportsDeferredExpiry() {
        return false;
    }
    
    /**
     * 系统关闭时的清理逻辑
     * 实现类需负责释放存储内所有 FlowEntry 的引用计数
     */
    void shutdown();
    
    /**
     * 可选：根据 Key 移除并获取上下文
     * 仅在实现类支持 Key-Value 语义时有效（如 Caffeine 实现）
     */
    default void remove(String key) {
        throw new UnsupportedOperationException("This storage does not support key-based retrieval.");
    }
    
    /**
     * 可选：已提交到消费执行器的移除回调数（仅 Caffeine 等有驱逐回调的实现有值，用于诊断「待消费」积压）。
     */
    default long getRemovalSubmittedCount() {
        return 0L;
    }
    
    default boolean requeue(FlowEntry<T> entry) {
        return deposit(entry);
    }
    
    /**
     * 当 source 已结束时，将存储内剩余条目排空并交给 finalizer（SINGLE_CONSUMED）。
     * 仅 Caffeine 等支持「完成时排空」的实现覆写；默认无操作。
     *
     * @return 本次排空并提交的条目数
     */
    default int drainRemainingToFinalizer() {
        return 0;
    }
}
