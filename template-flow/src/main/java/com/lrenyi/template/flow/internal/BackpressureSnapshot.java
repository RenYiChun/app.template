package com.lrenyi.template.flow.internal;

public final class BackpressureSnapshot {
    private final long storageUsed;
    private final long storageLimit;
    private final int inFlightProductionUsed;
    private final int inFlightProductionLimit;
    private final int producerConcurrencyUsed;
    private final int producerConcurrencyLimit;
    private final int inFlightConsumptionUsed;
    private final int inFlightConsumptionLimit;
    private final int consumerConcurrencyUsed;
    private final int consumerConcurrencyLimit;

    public BackpressureSnapshot(long storageUsed,
            long storageLimit,
            int inFlightProductionUsed,
            int inFlightProductionLimit,
            int producerConcurrencyUsed,
            int producerConcurrencyLimit,
            int inFlightConsumptionUsed,
            int inFlightConsumptionLimit,
            int consumerConcurrencyUsed,
            int consumerConcurrencyLimit) {
        this.storageUsed = storageUsed;
        this.storageLimit = storageLimit;
        this.inFlightProductionUsed = inFlightProductionUsed;
        this.inFlightProductionLimit = inFlightProductionLimit;
        this.producerConcurrencyUsed = producerConcurrencyUsed;
        this.producerConcurrencyLimit = producerConcurrencyLimit;
        this.inFlightConsumptionUsed = inFlightConsumptionUsed;
        this.inFlightConsumptionLimit = inFlightConsumptionLimit;
        this.consumerConcurrencyUsed = consumerConcurrencyUsed;
        this.consumerConcurrencyLimit = consumerConcurrencyLimit;
    }

    public long getStorageUsed() {
        return storageUsed;
    }

    public long getStorageLimit() {
        return storageLimit;
    }

    public int getInFlightProductionUsed() {
        return inFlightProductionUsed;
    }

    public int getInFlightProductionLimit() {
        return inFlightProductionLimit;
    }

    public int getProducerConcurrencyUsed() {
        return producerConcurrencyUsed;
    }

    public int getProducerConcurrencyLimit() {
        return producerConcurrencyLimit;
    }

    public int getInFlightConsumptionUsed() {
        return inFlightConsumptionUsed;
    }

    public int getInFlightConsumptionLimit() {
        return inFlightConsumptionLimit;
    }

    public int getConsumerConcurrencyUsed() {
        return consumerConcurrencyUsed;
    }

    public int getConsumerConcurrencyLimit() {
        return consumerConcurrencyLimit;
    }
}

