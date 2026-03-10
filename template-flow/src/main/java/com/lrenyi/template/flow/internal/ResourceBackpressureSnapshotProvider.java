package com.lrenyi.template.flow.internal;

import java.util.concurrent.Semaphore;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.storage.FlowStorage;

/**
 * 基于存储与 Job 级信号量的多维背压快照提供者。
 */
public final class ResourceBackpressureSnapshotProvider implements BackpressureSnapshotProvider {
    private final FlowStorage<?> storage;
    private final TemplateConfigProperties.Flow.Limits limits;
    private final Semaphore jobProducerSemaphore;
    private final Semaphore inFlightProductionSemaphore;
    private final Semaphore jobConsumerSemaphore;
    private final Semaphore pendingConsumerSlotSemaphore;

    public ResourceBackpressureSnapshotProvider(FlowStorage<?> storage,
            TemplateConfigProperties.Flow.Limits limits,
            Semaphore jobProducerSemaphore,
            Semaphore inFlightProductionSemaphore,
            Semaphore jobConsumerSemaphore,
            Semaphore pendingConsumerSlotSemaphore) {
        this.storage = storage;
        this.limits = limits;
        this.jobProducerSemaphore = jobProducerSemaphore;
        this.inFlightProductionSemaphore = inFlightProductionSemaphore;
        this.jobConsumerSemaphore = jobConsumerSemaphore;
        this.pendingConsumerSlotSemaphore = pendingConsumerSlotSemaphore;
    }

    @Override
    public BackpressureSnapshot snapshot() {
        TemplateConfigProperties.Flow.PerJob perJob = limits.getPerJob();
        long storageUsed = storage.supportsDeferredExpiry() ? storage.savedEntries() : storage.size();
        long storageLimit = storage.supportsDeferredExpiry() ? storage.entryLimit() : perJob.getStorageCapacity();

        int producerLimit = perJob.getProducerThreads();
        int producerUsed = 0;
        if (producerLimit > 0 && jobProducerSemaphore != null) {
            producerUsed = producerLimit - jobProducerSemaphore.availablePermits();
        }

        int inFlightLimit = perJob.getInFlightProduction();
        int inFlightUsed = 0;
        if (inFlightLimit > 0 && inFlightProductionSemaphore != null) {
            inFlightUsed = inFlightLimit - inFlightProductionSemaphore.availablePermits();
        }

        int consumerLimit = perJob.getConsumerThreads();
        int consumerUsed = 0;
        if (consumerLimit > 0 && jobConsumerSemaphore != null) {
            consumerUsed = consumerLimit - jobConsumerSemaphore.availablePermits();
        }

        int pendingLimit = perJob.getEffectivePendingConsumer();
        int pendingUsed = 0;
        if (pendingLimit > 0 && pendingConsumerSlotSemaphore != null) {
            pendingUsed = pendingLimit - pendingConsumerSlotSemaphore.availablePermits();
        }

        return new BackpressureSnapshot(storageUsed,
                storageLimit,
                inFlightUsed,
                inFlightLimit,
                producerUsed,
                producerLimit,
                pendingUsed,
                pendingLimit,
                consumerUsed,
                consumerLimit);
    }
}

