package com.lrenyi.template.flow.internal;

public final class DefaultBackpressurePolicy implements BackpressurePolicy {
    private final long waitIntervalMs;

    public DefaultBackpressurePolicy(long waitIntervalMs) {
        this.waitIntervalMs = waitIntervalMs > 0 ? waitIntervalMs : 100L;
    }

    public DefaultBackpressurePolicy() {
        this(100L);
    }

    @Override
    public BackpressureDecision decide(BackpressureSnapshot snapshot) {
        // 1) 存储容量维度
        long storageLimit = snapshot.getStorageLimit();
        long storageUsed = snapshot.getStorageUsed();
        if (storageLimit > 0 && storageUsed >= storageLimit) {
            return BackpressureDecision.block(waitIntervalMs, "storage_full");
        }

        // 2) 在途生产维度
        int inFlightLimit = snapshot.getInFlightProductionLimit();
        int inFlightUsed = snapshot.getInFlightProductionUsed();
        if (inFlightLimit > 0 && inFlightUsed >= inFlightLimit) {
            return BackpressureDecision.block(waitIntervalMs, "in_flight_production_full");
        }

        // 3) 等待消费（已离库未终结）维度
        int pendingLimit = snapshot.getInFlightConsumptionLimit();
        int pendingUsed = snapshot.getInFlightConsumptionUsed();
        if (pendingLimit > 0 && pendingUsed >= pendingLimit) {
            return BackpressureDecision.block(waitIntervalMs, "pending_consumer_full");
        }

        // 4) 消费并发维度
        int consumerLimit = snapshot.getConsumerConcurrencyLimit();
        int consumerUsed = snapshot.getConsumerConcurrencyUsed();
        if (consumerLimit > 0 && consumerUsed >= consumerLimit) {
            return BackpressureDecision.block(waitIntervalMs, "consumer_concurrency_full");
        }

        // 5) 生产并发维度（通常由线程池/信号量直接约束，这里仅作为附加信号）
        int producerLimit = snapshot.getProducerConcurrencyLimit();
        int producerUsed = snapshot.getProducerConcurrencyUsed();
        if (producerLimit > 0 && producerUsed >= producerLimit) {
            return BackpressureDecision.block(waitIntervalMs, "producer_concurrency_full");
        }

        return BackpressureDecision.proceed();
    }
}

