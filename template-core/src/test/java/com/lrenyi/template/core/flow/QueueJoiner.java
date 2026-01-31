package com.lrenyi.template.core.flow;

import com.lrenyi.template.core.flow.config.FlowStorageType;
import com.lrenyi.template.core.flow.source.FlowSourceAdapters;
import com.lrenyi.template.core.flow.source.FlowSourceProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 测试用 Joiner：Queue 存储，FIFO 消费。
 * 统计 onConsume 调用顺序与次数。
 */
public class QueueJoiner implements FlowJoiner<PairItem> {

    private volatile FlowSourceProvider<PairItem> sourceProvider;
    private final List<PairItem> consumedOrder = new CopyOnWriteArrayList<>();
    private final Map<FailureReason, AtomicLong> onFailedByReason = new ConcurrentHashMap<>();

    {
        for (FailureReason r : FailureReason.values()) {
            onFailedByReason.put(r, new AtomicLong(0));
        }
    }

    public void setSourceProvider(FlowSourceProvider<PairItem> sourceProvider) {
        this.sourceProvider = sourceProvider;
    }

    @Override
    public FlowStorageType getStorageType() {
        return FlowStorageType.QUEUE;
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
        // Queue 模式一般不配对
    }

    @Override
    public void onConsume(PairItem item, String jobId) {
        consumedOrder.add(item);
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

    public long getOnFailedCount(FailureReason reason) {
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
