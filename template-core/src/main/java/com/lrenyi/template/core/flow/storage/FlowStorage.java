package com.lrenyi.template.core.flow.storage;

import com.lrenyi.template.core.flow.context.FlowEntry;

/**
 * 任务存储抽象接口
 * 职责：负责 FlowEntry 的暂存、检索及生命周期维护。
 * <p>
 * 若实现类需要「从存储取出数据」时在单物理线程中 acquire 再交给虚拟线程处理（防 OOM），
 * 应使用 FlowManager.getStorageEgressExecutor() 作为该离场路径的 executor。
 */
public interface FlowStorage<T> {
    
    default void deposit(FlowEntry<T> entry) {
        if (entry == null) {
            return;
        }
        // 1. 物理引用 +1 (确保在 Storage 期间对象不被销毁)
        entry.retain();
        // 2. 调用具体的物理存储逻辑 (由子类实现)
        boolean success = doDeposit(entry);
        if (!success) {
            entry.release();
        }
    }
    
    /**
     * 将任务上下文存入存储区
     * * @param ctx 任务上下文
     *
     * @return true 代表存入成功；
     * false 代表存储已满或拒绝准入，此时框架应触发 onRejected 回调
     */
    boolean doDeposit(FlowEntry<T> ctx);
    
    long size();
    
    long maxCacheSize();
    
    /**
     * 系统关闭时的清理逻辑
     * 实现类需负责释放存储内所有 FlowEntry 的引用计数
     */
    void shutdown();
    
    /**
     * 可选：根据 Key 移除并获取上下文
     * 仅在实现类支持 Key-Value 语义时有效（如 Caffeine 实现）
     */
    default FlowEntry<T> remove(String key) {
        throw new UnsupportedOperationException("This storage does not support key-based retrieval.");
    }
}