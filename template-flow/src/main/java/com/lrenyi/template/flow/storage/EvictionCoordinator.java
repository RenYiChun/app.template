package com.lrenyi.template.flow.storage;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * 驱逐协调器，从 DelayQueue 中取出 SlotExpiryToken 并交给存储处理。
 * 支持多线程：多个 worker 共享同一 ExpiryIndex，各自 take/poll 获取 token 并执行 drain。
 * 当 scanIntervalMill > 0 时使用 poll(timeout)，定期唤醒以检查关闭状态；否则使用 take() 阻塞等待。
 */
@Slf4j
public final class EvictionCoordinator implements AutoCloseable {
    private final ExpiryIndex<SlotExpiryToken> expiryIndex;
    private final BoundedTimedFlowStorage<?> storage;
    private final long scanIntervalMill;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Thread[] workers;

    public EvictionCoordinator(ExpiryIndex<SlotExpiryToken> expiryIndex,
            BoundedTimedFlowStorage<?> storage,
            String threadNamePrefix,
            int threadCount,
            long scanIntervalMill) {
        this.expiryIndex = Objects.requireNonNull(expiryIndex, "expiryIndex");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.scanIntervalMill = Math.max(0, scanIntervalMill);
        int count = Math.max(1, threadCount);
        this.workers = new Thread[count];
        for (int i = 0; i < count; i++) {
            String name = count > 1 ? threadNamePrefix + "-" + i : threadNamePrefix;
            workers[i] = Thread.ofVirtual().name(name).unstarted(this::runLoop);
        }
    }

    public void start() {
        for (Thread w : workers) {
            w.start();
        }
    }

    private void runLoop() {
        try {
            log.debug("EvictionCoordinator started, waiting for expiry tokens (scanIntervalMill={})", scanIntervalMill);
            while (!closed.get()) {
                SlotExpiryToken token = scanIntervalMill > 0
                        ? expiryIndex.poll(scanIntervalMill, TimeUnit.MILLISECONDS)
                        : expiryIndex.take();
                if (token == null) {
                    continue;
                }
                try {
                    if (log.isTraceEnabled()) {
                        log.trace("EvictionCoordinator processing token slotId={}, nextCheckAt={}",
                                  token.slotId(),
                                  token.nextCheckAt()
                        );
                    }
                    storage.drainExpiredEntries(token.slotId());
                } catch (Throwable t) {
                    log.error("Failed to handle SlotExpiryToken for slotId={}", token.slotId(), t);
                }
            }
        } catch (InterruptedException e) {
            if (!closed.get()) {
                log.warn("EvictionCoordinator interrupted unexpectedly", e);
            }
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            log.error("EvictionCoordinator loop failed", t);
        } finally {
            log.info("EvictionCoordinator stopped");
        }
    }
    
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (Thread w : workers) {
            if (w != null) {
                w.interrupt();
            }
        }
    }
}

