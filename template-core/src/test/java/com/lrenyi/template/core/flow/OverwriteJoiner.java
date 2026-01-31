package com.lrenyi.template.core.flow;

import com.lrenyi.template.core.flow.config.FlowStorageType;
import com.lrenyi.template.core.flow.source.FlowSourceAdapters;
import com.lrenyi.template.core.flow.source.FlowSourceProvider;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 测试用 Joiner：单 Key 覆盖（needMatched=false），Caffeine 存储。
 * 统计 onFailed(..., REPLACE) 与 onConsume 调用次数。
 */
public class OverwriteJoiner implements FlowJoiner<PairItem> {

    private volatile FlowSourceProvider<PairItem> sourceProvider;
    private final AtomicLong onConsumeCount = new AtomicLong(0);
    private final Map<FailureReason, AtomicLong> onFailedByReason = new ConcurrentHashMap<>();

    public OverwriteJoiner() {
        for (FailureReason r : FailureReason.values()) {
            onFailedByReason.put(r, new AtomicLong(0));
        }
    }

    public void setSourceProvider(FlowSourceProvider<PairItem> sourceProvider) {
        this.sourceProvider = sourceProvider;
    }

    @Override
    public FlowStorageType getStorageType() {
        return FlowJoiner.super.getStorageType();
    }

    @Override
    public Class<PairItem> getDataType() {
        return PairItem.class;
    }

    @Override
    public FlowSourceProvider<PairItem> sourceProvider() {
        return sourceProvider != null ? sourceProvider : FlowSourceAdapters.emptyProvider();
    }

    @Override
    public String joinKey(PairItem item) {
        return item.getId();
    }

    @Override
    public void onSuccess(PairItem existing, PairItem incoming, String jobId) {
        // 覆盖模式一般不触发 onSuccess
    }

    @Override
    public void onConsume(PairItem item, String jobId) {
        onConsumeCount.incrementAndGet();
    }

    @Override
    public void onFailed(PairItem item, String jobId, FailureReason reason) {
        if (reason != null) {
            onFailedByReason.computeIfAbsent(reason, k -> new AtomicLong(0)).incrementAndGet();
        }
    }

    @Override
    public void onFailed(PairItem item, String jobId) {
        onFailedByReason.get(FailureReason.UNKNOWN).incrementAndGet();
    }

    public long getOnConsumeCount() {
        return onConsumeCount.get();
    }

    public long getOnFailedCount(FailureReason reason) {
        AtomicLong counter = onFailedByReason.get(reason);
        return counter != null ? counter.get() : 0;
    }

    public void resetCounts() {
        onConsumeCount.set(0);
        onFailedByReason.forEach((k, v) -> v.set(0));
    }
}
