package com.lrenyi.template.flow;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.model.FlowStorageType;
import com.lrenyi.template.flow.model.FailureReason;

/**
 * 测试用 Joiner：双流配对（needMatched=true），Caffeine 存储。
 * 统计 onSuccess/onFailed 调用次数与失败原因。
 */
public class PairingJoiner implements FlowJoiner<PairItem> {

    private volatile FlowSourceProvider<PairItem> sourceProvider;
    private final AtomicLong onSuccessCount = new AtomicLong(0);
    private final Map<FailureReason, AtomicLong> onFailedByReason = new ConcurrentHashMap<>();

    public PairingJoiner() {
        for (FailureReason r : FailureReason.values()) {
            onFailedByReason.put(r, new AtomicLong(0));
        }
    }

    public void setSourceProvider(FlowSourceProvider<PairItem> sourceProvider) {
        this.sourceProvider = sourceProvider;
    }

    @Override
    public FlowStorageType getStorageType() {
        return FlowStorageType.CAFFEINE;
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
    public boolean needMatched() {
        return true;
    }

    @Override
    public void onSuccess(PairItem existing, PairItem incoming, String jobId) {
        onSuccessCount.incrementAndGet();
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

    public long getOnSuccessCount() {
        return onSuccessCount.get();
    }

    public long getOnFailedCount(FailureReason reason) {
        AtomicLong counter = onFailedByReason.get(reason);
        return counter != null ? counter.get() : 0;
    }

    public void resetCounts() {
        onSuccessCount.set(0);
        onFailedByReason.forEach((k, v) -> v.set(0));
    }
}
