package com.lrenyi.template.flow.sources.nats;

import com.lrenyi.template.core.flow.source.FlowSource;
import io.nats.client.Message;
import io.nats.client.Subscription;
import java.time.Duration;
import java.util.NoSuchElementException;

/**
 * 单子流 NATS 数据源：包装一个 {@link Subscription}，按顺序产出 T。
 * 由引擎在虚拟线程中拉取（hasNext/next）；{@link #hasNext()} 内无消息时调用
 * {@code subscription.nextMessage(timeout)} 阻塞等待，可被 {@link #close()} 打断（unsubscribe 后 nextMessage 抛异常）。
 * <p>
 * 阻塞与中断：若当前线程被中断则抛 {@link InterruptedException}；close 后 nextMessage 可能抛 {@link IllegalStateException}，内部视为流结束。
 *
 * @param <T> 业务产出类型，由 mapper 从 {@link Message} 转换得到
 */
public final class NatsFlowSource<T> implements FlowSource<T> {

    private final Subscription subscription;
    private final java.util.function.Function<Message, T> mapper;
    private final Duration nextMessageTimeout;

    private Message nextMessage;
    private boolean closed;

    /**
     * @param subscription       已 subscribe 的 NATS 订阅，调用方负责创建；本类负责在 close() 时 unsubscribe
     * @param mapper             Message 转 T，非 null
     * @param nextMessageTimeout 单次 nextMessage 等待时长，非 null
     */
    public NatsFlowSource(Subscription subscription,
                          java.util.function.Function<Message, T> mapper,
                          Duration nextMessageTimeout) {
        if (subscription == null) {
            throw new IllegalArgumentException("subscription 非 null");
        }
        if (mapper == null) {
            throw new IllegalArgumentException("mapper 非 null");
        }
        this.subscription = subscription;
        this.mapper = mapper;
        this.nextMessageTimeout = nextMessageTimeout != null ? nextMessageTimeout : Duration.ofSeconds(1);
    }

    @Override
    public boolean hasNext() throws InterruptedException {
        if (closed) {
            return false;
        }
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (nextMessage != null) {
            return true;
        }
        while (!closed) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            try {
                Message msg = subscription.nextMessage(nextMessageTimeout);
                if (msg != null) {
                    nextMessage = msg;
                    return true;
                }
            } catch (IllegalStateException e) {
                closed = true;
                return false;
            }
        }
        return false;
    }

    @Override
    public T next() {
        if (closed) {
            throw new NoSuchElementException();
        }
        if (nextMessage == null) {
            throw new NoSuchElementException();
        }
        Message msg = nextMessage;
        nextMessage = null;
        return mapper.apply(msg);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            subscription.unsubscribe();
        } catch (Exception ignored) {
            // 尽量释放
        }
    }
}
