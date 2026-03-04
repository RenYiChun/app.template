package com.lrenyi.template.flow.resource;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 双层许可获取工具：先 global 再 per-job，释放顺序相反。
 * 失败时立即回滚已占用的许可，避免泄漏。
 */
public final class PermitPair {
    private final Semaphore globalSemaphore;
    private final Semaphore perJobSemaphore;
    private final int permits;
    private boolean acquired;
    
    private PermitPair(Semaphore globalSemaphore, Semaphore perJobSemaphore, int permits) {
        this.globalSemaphore = globalSemaphore;
        this.perJobSemaphore = perJobSemaphore;
        this.permits = permits;
        this.acquired = false;
    }
    
    /**
     * 尝试获取两个信号量：先 global，再 per-job。
     * 任一步失败则立即 release 已占用的，返回 false。
     *
     * @param globalSemaphore  全局信号量（可为 null 表示不启用）
     * @param perJobSemaphore  每 Job 信号量（必须非 null）
     * @param permits         许可数量
     * @return true 表示全部获取成功；false 表示失败或已回滚
     */
    public static boolean tryAcquireBoth(Semaphore globalSemaphore,
            Semaphore perJobSemaphore,
            int permits) throws InterruptedException {
        if (perJobSemaphore == null) {
            return false;
        }
        boolean globalAcquired = false;
        try {
            if (globalSemaphore != null && permits > 0) {
                globalSemaphore.acquire(permits);
                globalAcquired = true;
            }
            perJobSemaphore.acquire(permits);
            return true;
        } catch (InterruptedException e) {
            if (globalAcquired && globalSemaphore != null) {
                globalSemaphore.release(permits);
            }
            throw e;
        } catch (Exception e) {
            if (globalAcquired && globalSemaphore != null) {
                globalSemaphore.release(permits);
            }
            return false;
        }
    }
    
    /**
     * 带超时的 acquire。
     */
    public static boolean tryAcquireBoth(Semaphore globalSemaphore,
            Semaphore perJobSemaphore,
            int permits,
            long timeout,
            TimeUnit unit) throws InterruptedException {
        if (perJobSemaphore == null) {
            return false;
        }
        boolean globalAcquired = false;
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        try {
            if (globalSemaphore != null && permits > 0) {
                long remaining = deadline - System.nanoTime();
                if (!globalSemaphore.tryAcquire(permits, remaining, TimeUnit.NANOSECONDS)) {
                    return false;
                }
                globalAcquired = true;
            }
            long remaining = deadline - System.nanoTime();
            if (!perJobSemaphore.tryAcquire(permits, remaining, TimeUnit.NANOSECONDS)) {
                if (globalAcquired && globalSemaphore != null) {
                    globalSemaphore.release(permits);
                }
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            if (globalAcquired && globalSemaphore != null) {
                globalSemaphore.release(permits);
            }
            throw e;
        }
    }
    
    /**
     * 创建已持有许可的 PermitPair，用于持有后释放。
     * 调用方需确保已成功 acquire 两个信号量。
     */
    public static PermitPair createHeld(Semaphore globalSemaphore,
            Semaphore perJobSemaphore,
            int permits) {
        PermitPair pair = new PermitPair(globalSemaphore, perJobSemaphore, permits);
        pair.acquired = true;
        return pair;
    }
    
    /**
     * 按先 per-job 再 global 顺序释放。
     */
    public void release() {
        if (!acquired) {
            return;
        }
        acquired = false;
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
}
