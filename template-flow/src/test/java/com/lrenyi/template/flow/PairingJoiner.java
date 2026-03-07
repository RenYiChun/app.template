package com.lrenyi.template.flow;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.model.EgressReason;
import lombok.Setter;

/**
 * 测试用 Joiner：双流配对（needMatched=true），Caffeine 存储。
 * 统计 onSuccess/onFailed 调用次数与失败原因。
 */
public class PairingJoiner implements FlowJoiner<PairItem> {
    
    private final AtomicLong onSuccessCount = new AtomicLong(0);
    private final Map<EgressReason, AtomicLong> onFailedByReason = new ConcurrentHashMap<>();
    @Setter
    private volatile FlowSourceProvider<PairItem> sourceProvider;
    
    public PairingJoiner() {
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
        onSuccessCount.incrementAndGet();
    }
    
    @Override
    public void onSingleConsumed(PairItem item, String jobId, EgressReason reason) {
        onFailedByReason.computeIfAbsent(reason, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    @Override
    public boolean needMatched() {
        return true;
    }
    
    public long getOnSuccessCount() {
        return onSuccessCount.get();
    }
    
    public long getOnFailedCount(EgressReason reason) {
        AtomicLong counter = onFailedByReason.get(reason);
        return counter != null ? counter.get() : 0;
    }
    
    public void resetCounts() {
        onSuccessCount.set(0);
        onFailedByReason.forEach((k, v) -> v.set(0));
    }
}
