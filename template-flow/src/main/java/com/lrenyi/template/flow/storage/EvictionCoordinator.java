package com.lrenyi.template.flow.storage;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * 单线程驱逐协调器，从 DelayQueue 中取出 SlotExpiryToken 并交给存储处理。
 */
@Slf4j
public final class EvictionCoordinator implements AutoCloseable {
    private final ExpiryIndex<SlotExpiryToken> expiryIndex;
    private final BoundedTimedFlowStorage<?> storage;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private Thread worker;

    public EvictionCoordinator(ExpiryIndex<SlotExpiryToken> expiryIndex,
                               BoundedTimedFlowStorage<?> storage,
                               String threadName) {
        this.expiryIndex = Objects.requireNonNull(expiryIndex, "expiryIndex");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.worker = new Thread(this::runLoop, threadName);
        this.worker.setDaemon(true);
    }

    public void start() {
        worker.start();
    }

    private void runLoop() {
        try {
            log.debug("EvictionCoordinator started, waiting for expiry tokens");
            while (!closed.get()) {
                SlotExpiryToken token = expiryIndex.take();
                handleToken(token);
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

    private void handleToken(SlotExpiryToken token) {
        try {
            if (log.isTraceEnabled()) {
                log.trace("EvictionCoordinator processing token slotId={}, nextCheckAt={}", token.slotId(), token.nextCheckAt());
            }
            storage.onExpiryToken(token);
        } catch (Throwable t) {
            log.error("Failed to handle SlotExpiryToken for slotId={}", token.slotId(), t);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (worker != null) {
            worker.interrupt();
        }
    }
}

