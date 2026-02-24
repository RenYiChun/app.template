package com.lrenyi.template.flow.sources.nats;

import java.time.Duration;
import java.util.List;
import com.lrenyi.template.core.flow.api.FlowSource;
import com.lrenyi.template.core.flow.api.FlowSourceProvider;
import io.nats.client.Message;
import io.nats.client.Subscription;

/**
 * 多子流 NATS 提供者：为每个 {@link Subscription} 提供一个 {@link FlowSource}，
 * 引擎会在独立虚拟线程中消费每个子流，实现多 subject/queue 并行拉取。
 * <p>
 * <b>适用场景</b>：所有订阅产出的业务类型一致（同一 mapper 将 {@link Message} 转为同一 T）。
 * 例如：同一 subject 的多个 queue 订阅、或多个 subject 但消息都映射为同一 DTO。
 * <p>
 * <b>若不同 Subject 对应不同 T</b>：不要用本 Provider。应改为（二选一）：
 * <ul>
 *   <li>每个 subject 单独建一个 {@link NatsFlowSource}，对应单独的 run/Job，各自一种 T；</li>
 *   <li>或先把所有 Message 映射为统一的「信封」类型 T（如含 subject + body），再在本 Provider 里用同一 mapper。</li>
 * </ul>
 *
 * @param <T> 业务产出类型，由 mapper 从 {@link Message} 转换得到（所有订阅共用同一 T）
 */
public final class NatsFlowSourceProvider<T> implements FlowSourceProvider<T> {
    
    private final FlowSourceProvider<T> delegate;
    
    /**
     * @param subscriptions      已 subscribe 的 NATS 订阅列表，非 null 非空；每元素对应一个子流
     * @param mapper             Message 转 T，非 null
     * @param nextMessageTimeout 单次 nextMessage 等待时长，非 null
     */
    public NatsFlowSourceProvider(List<Subscription> subscriptions,
                                  java.util.function.Function<Message, T> mapper,
                                  Duration nextMessageTimeout) {
        if (subscriptions == null || subscriptions.isEmpty()) {
            throw new IllegalArgumentException("subscriptions 非空");
        }
        if (mapper == null) {
            throw new IllegalArgumentException("mapper 非 null");
        }
        Duration timeout = nextMessageTimeout != null ? nextMessageTimeout : Duration.ofSeconds(1);
        List<FlowSource<T>> sources;
        sources = subscriptions.stream().<FlowSource<T>>map(sub -> new NatsFlowSource<>(sub, mapper, timeout)).toList();
        this.delegate = com.lrenyi.template.core.flow.api.FlowSourceAdapters.fromFlowSources(sources);
    }
    
    @Override
    public boolean hasNextSubSource() throws InterruptedException {
        return delegate.hasNextSubSource();
    }
    
    @Override
    public FlowSource<T> nextSubSource() {
        return delegate.nextSubSource();
    }
    
    @Override
    public void close() {
        delegate.close();
    }
}
