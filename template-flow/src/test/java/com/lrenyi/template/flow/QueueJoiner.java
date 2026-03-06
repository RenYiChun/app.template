package com.lrenyi.template.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.model.EgressReason;

/**
 * 测试用 Joiner：Queue 存储，FIFO 消费。
 * 统计 onConsume 调用顺序与次数。
 */
public class QueueJoiner implements FlowJoiner<PairItem> {
    
    private final List<PairItem> consumedOrder = new CopyOnWriteArrayList<>();
    private final Map<EgressReason, AtomicLong> onFailedByReason = new ConcurrentHashMap<>();
    private volatile FlowSourceProvider<PairItem> sourceProvider;
    
    public QueueJoiner() {
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
            consumedOrder.add(item);
            return;
        }
        onFailedByReason.computeIfAbsent(reason, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    public long getOnFailedCount(EgressReason reason) {
        AtomicLong counter = onFailedByReason.get(reason);
        return counter != null ? counter.get() : 0;
    }
    
    public List<PairItem> getConsumedOrder() {
        return new ArrayList<>(consumedOrder);
    }
    
    public int getConsumedCount() {
        return consumedOrder.size();
    }
    
    public void resetCounts() {
        consumedOrder.clear();
        onFailedByReason.forEach((k, v) -> v.set(0));
    }
}
