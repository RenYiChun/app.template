package com.lrenyi.template.flow;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.model.EgressReason;

/**
 * 测试用 Joiner：单 Key 覆盖（needMatched=false），Caffeine 存储。
 * 统计 onFailed(..., REPLACE) 与 onConsume 调用次数。
 */
public class OverwriteJoiner implements FlowJoiner<PairItem> {
    
    private final AtomicLong onConsumeCount = new AtomicLong(0);
    private final Map<EgressReason, AtomicLong> onFailedByReason = new ConcurrentHashMap<>();
    private volatile FlowSourceProvider<PairItem> sourceProvider;
    
    public OverwriteJoiner() {
        for (EgressReason r : EgressReason.values()) {
            onFailedByReason.put(r, new AtomicLong(0));
        }
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
    public void onPairConsumed(PairItem existing, PairItem incoming, String jobId) {
    
    }
    
    @Override
    public void onSingleConsumed(PairItem item, String jobId, EgressReason reason) {
        if (reason == EgressReason.SINGLE_CONSUMED) {
            onConsumeCount.incrementAndGet();
            return;
        }
        onFailedByReason.computeIfAbsent(reason, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    public long getOnConsumeCount() {
        return onConsumeCount.get();
    }
    
    public long getOnFailedCount(EgressReason reason) {
        AtomicLong counter = onFailedByReason.get(reason);
        return counter != null ? counter.get() : 0;
    }
    
    public void resetCounts() {
        onConsumeCount.set(0);
        onFailedByReason.forEach((k, v) -> v.set(0));
    }
}
