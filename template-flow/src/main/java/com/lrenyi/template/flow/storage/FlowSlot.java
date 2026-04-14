package com.lrenyi.template.flow.storage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.model.EgressReason;

/**
 * 流式存储槽位：封装同 key 下多 value 的 Deque 结构，支持 append、poll、overflow 策略。
 * 单值模式（maxPerKey=1）与多值模式共用同一结构。
 *
 * @param <T> 存储的数据类型
 */
public final class FlowSlot<T> {
    
    /** 溢出时被淘汰的条目及原因 */
    public record OverflowResult<E>(FlowEntry<E> entry, EgressReason reason) {}
    
    private final Deque<FlowEntry<T>> deque;
    private final int maxPerKey;
    private final TemplateConfigProperties.Flow.MultiValueOverflowPolicy overflowPolicy;
    // Slot 级调度元信息（超时按 key：以该 key 下首次写入时间为起点）
    private final String slotId;
    /** 该 key 下首次写入的时间（epoch ms），用于计算 slot 级过期点 */
    private long earliestStoredAtEpochMs;
    private long earliestExpireAt;
    private long nextCheckAt;
    private boolean queuedForExpiry;
    /** 是否正在配对中（配对消费执行期间为 true），驱逐时若为 true 则跳过本次驱逐 */
    private volatile boolean pairingInProgress;

    /**
     * 创建槽位（超时按 key：createdAtEpochMs 为该 key 下首次写入时间，用于计算 slot 级过期）。
     *
     * @param slotId 槽位 ID
     * @param maxPerKey 每 key 最大条目数
     * @param overflowPolicy 溢出策略
     * @param createdAtEpochMs 该 key 首次写入时间（epoch ms）
     */
    public FlowSlot(String slotId, int maxPerKey, TemplateConfigProperties.Flow.MultiValueOverflowPolicy overflowPolicy,
            long createdAtEpochMs) {
        this.slotId = slotId;
        this.deque = new ArrayDeque<>(Math.max(1, maxPerKey));
        this.maxPerKey = Math.max(1, maxPerKey);
        this.overflowPolicy = overflowPolicy != null ? overflowPolicy :
                TemplateConfigProperties.Flow.MultiValueOverflowPolicy.DROP_OLDEST;
        this.earliestStoredAtEpochMs = createdAtEpochMs;
    }
    
    /**
     * 向队尾追加 entry。
     *
     * @param entry 待追加的条目
     * @return 若发生 overflow 被淘汰的条目及原因
     */
    public Optional<OverflowResult<T>> append(FlowEntry<T> entry) {
        if (maxPerKey <= 0) {
            return Optional.of(new OverflowResult<>(entry, EgressReason.OVERFLOW_DROP_NEWEST));
        }
        if (deque.size() < maxPerKey) {
            deque.addLast(entry);
            return Optional.empty();
        }
        switch (overflowPolicy) {
            case DROP_NEWEST:
                return Optional.of(new OverflowResult<>(entry, EgressReason.OVERFLOW_DROP_NEWEST));
            case DROP_OLDEST:
            default:
                FlowEntry<T> d = deque.pollFirst();
                deque.addLast(entry);
                return Optional.ofNullable(d).map(x -> new OverflowResult<>(x, EgressReason.OVERFLOW_DROP_OLDEST));
        }
    }
    
    /**
     * 从队首移除并返回。
     */
    public Optional<FlowEntry<T>> poll() {
        return Optional.ofNullable(deque.pollFirst());
    }
    
    /**
     * 获取队首元素但不移除。
     */
    public Optional<FlowEntry<T>> peek() {
        return Optional.ofNullable(deque.peekFirst());
    }
    
    /**
     * 槽位内 entry 数量。
     */
    public int size() {
        return deque.size();
    }
    
    /**
     * 是否为空。
     */
    public boolean isEmpty() {
        return deque.isEmpty();
    }
    
    /**
     * 获取槽位内所有 entry 的副本（用于驱逐时全量配对处理）。
     * 调用后原槽位不会被修改，调用方负责处理返回的条目。
     */
    public List<FlowEntry<T>> drainAll() {
        List<FlowEntry<T>> result = new ArrayList<>(deque);
        deque.clear();
        return result;
    }
    
    /**
     * 将 entry 放回队首（用于 preRetry 时 partner 回写）。
     */
    public void offerFirst(FlowEntry<T> entry) {
        deque.addFirst(entry);
    }
    
    /**
     * 将多个 entry 按顺序放回队首（用于配对失败回写，partner 在前、entry 在后）。
     */
    public void offerFirstAll(List<FlowEntry<T>> entries) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            deque.addFirst(entries.get(i));
        }
    }
    
    /**
     * 将 entry 放回队尾（用于多候选尝试时，不匹配的 partner 放回以便尝试下一个）。
     */
    public void offerLast(FlowEntry<T> entry) {
        deque.addLast(entry);
    }
    
    /**
     * 对槽位内所有 entry 执行操作（只读，不修改槽位）。
     */
    public void forEachEntry(Consumer<FlowEntry<T>> action) {
        for (FlowEntry<T> e : deque) {
            action.accept(e);
        }
    }
    
    public String getSlotId() {
        return slotId;
    }

    public long getEarliestStoredAtEpochMs() {
        return earliestStoredAtEpochMs;
    }

    public void setEarliestStoredAtEpochMs(long earliestStoredAtEpochMs) {
        this.earliestStoredAtEpochMs = earliestStoredAtEpochMs;
    }

    public long getEarliestExpireAt() {
        return earliestExpireAt;
    }

    public void setEarliestExpireAt(long earliestExpireAt) {
        this.earliestExpireAt = earliestExpireAt;
    }
    
    public long getNextCheckAt() {
        return nextCheckAt;
    }

    public void setNextCheckAt(long nextCheckAt) {
        this.nextCheckAt = nextCheckAt;
    }

    public boolean isQueuedForExpiry() {
        return queuedForExpiry;
    }

    public void setQueuedForExpiry(boolean queuedForExpiry) {
        this.queuedForExpiry = queuedForExpiry;
    }
    
    public boolean isPairingInProgress() {
        return pairingInProgress;
    }
    
    public void setPairingInProgress(boolean pairingInProgress) {
        this.pairingInProgress = pairingInProgress;
    }

    public Iterable<FlowEntry<T>> entries() {
        return deque;
    }

    public boolean remove(FlowEntry<T> entry) {
        return deque.remove(entry);
    }
}
