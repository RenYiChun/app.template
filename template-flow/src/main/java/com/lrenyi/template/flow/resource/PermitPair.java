package com.lrenyi.template.flow.resource;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 双层许可对：绑定 global + per-job 两个信号量，统一获取/释放顺序。
 * 建议在创建资源上下文时用 {@link #of(Semaphore, Semaphore)} 创建一次，各处引用同一实例。
 * 获取顺序：先 global 再 per-job；释放顺序：先 per-job 再 global。失败时立即回滚已占用的许可。
 */
public final class PermitPair {
    private final Semaphore globalSemaphore;
    private final Semaphore perJobSemaphore;
    
    private PermitPair(Semaphore globalSemaphore, Semaphore perJobSemaphore) {
        this.globalSemaphore = globalSemaphore;
        this.perJobSemaphore = perJobSemaphore;
    }
    
    /**
     * 创建双层许可对，供后续 acquire/release 使用。
     *
     * @param globalSemaphore 全局信号量（可为 null 表示仅用 per-job）
     * @param perJobSemaphore 每 Job 信号量（可为 null 表示仅用 global，如仅全局消费限制）
     */
    public static PermitPair of(Semaphore globalSemaphore, Semaphore perJobSemaphore) {
        return new PermitPair(globalSemaphore, perJobSemaphore);
    }

    /**
     * 尝试获取两个信号量：先 global，再 per-job。
     * 任一步失败则立即 release 已占用的，返回 false。
     *
     * @param permits 许可数量
     * @return true 表示全部获取成功；false 表示失败或已回滚
     */
    public boolean tryAcquireBoth(int permits) throws InterruptedException {
        if (permits <= 0) {
            return true;
        }
        if (perJobSemaphore == null && globalSemaphore == null) {
            return false;
        }
        if (perJobSemaphore == null) {
            globalSemaphore.acquire(permits);
            return true;
        }
        boolean globalAcquired = false;
        try {
            if (globalSemaphore != null) {
                globalSemaphore.acquire(permits);
                globalAcquired = true;
            }
            perJobSemaphore.acquire(permits);
            return true;
        } catch (InterruptedException e) {
            if (globalAcquired) {
                globalSemaphore.release(permits);
            }
            throw e;
        } catch (Exception e) {
            if (globalAcquired) {
                globalSemaphore.release(permits);
            }
            return false;
        }
    }

    /**
     * 带超时的 acquire：先 global，再 per-job。
     */
    public boolean tryAcquireBoth(int permits, long timeout, TimeUnit unit) throws InterruptedException {
        if (permits <= 0) {
            return true;
        }
        if (perJobSemaphore == null && globalSemaphore == null) {
            return false;
        }
        if (perJobSemaphore == null) {
            return globalSemaphore.tryAcquire(permits, timeout, unit);
        }
        boolean globalAcquired = false;
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        try {
            if (globalSemaphore != null) {
                long remaining = deadline - System.nanoTime();
                if (!globalSemaphore.tryAcquire(permits, remaining, TimeUnit.NANOSECONDS)) {
                    return false;
                }
                globalAcquired = true;
            }
            long remaining = deadline - System.nanoTime();
            if (!perJobSemaphore.tryAcquire(permits, remaining, TimeUnit.NANOSECONDS)) {
                if (globalAcquired) {
                    globalSemaphore.release(permits);
                }
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            if (globalAcquired) {
                globalSemaphore.release(permits);
            }
            throw e;
        }
    }

    /**
     * 按先 per-job 再 global 顺序释放指定数量许可。
     * 无条件释放：per-job 一定释放；global 一定释放（调用方需确保确实持有）。
     */
    public void release(int permits) {
        if (permits <= 0) {
            return;
        }
        try {
            if (perJobSemaphore != null) {
                perJobSemaphore.release(permits);
            }
            if (globalSemaphore != null) {
                globalSemaphore.release(permits);
            }
        } catch (Exception e) {
            // 已释放部分，无法回滚，仅记录
        }
    }
    
    /**
     * 释放许可，且仅在「当前 global 可用数 &lt; globalConcurrencyLimit」时释放 global（防御性，避免超限）。
     * 先释放 per-job，再按条件释放 global。所有释放逻辑均封装在此。
     *
     * @param permits                释放数量
     * @param globalConcurrencyLimit 全局并发上限，仅当 global 可用数 &lt; 此值时才 release global
     * @return 是否对 global 执行了 release（用于调用方打点/计数，不暴露信号量本身）
     */
    public boolean release(int permits, int globalConcurrencyLimit) {
        if (permits <= 0) {
            return false;
        }
        try {
            if (perJobSemaphore != null) {
                perJobSemaphore.release(permits);
            }
            if (globalSemaphore != null && globalSemaphore.availablePermits() < globalConcurrencyLimit) {
                globalSemaphore.release(permits);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 当前 global 信号量可用许可数；无 global 时返回 -1（仅用于内部判断/日志，不暴露信号量引用）。
     */
    public int getGlobalAvailablePermits() {
        return globalSemaphore != null ? globalSemaphore.availablePermits() : -1;
    }
    
    /**
     * 当前 per-job 信号量可用许可数；无 per-job 时返回 -1。
     */
    public int getPerJobAvailablePermits() {
        return perJobSemaphore != null ? perJobSemaphore.availablePermits() : -1;
    }
}
