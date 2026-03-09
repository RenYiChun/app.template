package com.lrenyi.template.flow.internal;

import com.lrenyi.template.flow.storage.FlowStorage;

/**
 * 仅基于存储使用情况的背压快照提供者。
 * <p>
 * 后续可以在此基础上扩展其它维度（在途生产、消费并发等）。
 */
public final class StorageBackpressureSnapshotProvider implements BackpressureSnapshotProvider {
    private final FlowStorage<?> storage;

    public StorageBackpressureSnapshotProvider(FlowStorage<?> storage) {
        this.storage = storage;
    }

    @Override
    public BackpressureSnapshot snapshot() {
        long used = storage.supportsDeferredExpiry() ? storage.usedEntries() : storage.size();
        long limit = storage.supportsDeferredExpiry() ? storage.entryLimit() : storage.maxCacheSize();
        // 仅提供存储维度，其余维度由 ResourceBackpressureSnapshotProvider 负责
        return new BackpressureSnapshot(used,
                limit,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0);
    }
}

