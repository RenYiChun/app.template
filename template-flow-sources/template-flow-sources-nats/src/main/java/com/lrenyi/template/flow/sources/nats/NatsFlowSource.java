package com.lrenyi.template.flow.sources.nats;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import com.lrenyi.template.core.flow.api.FlowSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.nats.client.Message;
import lombok.extern.slf4j.Slf4j;
import io.nats.client.Subscription;

/**
 * 单子流 NATS 数据源：包装一个 {@link Subscription}，按顺序产出 T。
 *
 * @param <T> 业务产出类型
 */
@Slf4j
public final class NatsFlowSource<T> implements FlowSource<T> {

    private final Subscription subscription;
    private final java.util.function.Function<Message, T> mapper;
    private final Duration nextMessageTimeout;
    private final MeterRegistry meterRegistry;

    private Message nextMessage;
    private boolean closed;

    public NatsFlowSource(Subscription subscription,
                          java.util.function.Function<Message, T> mapper,
                          Duration nextMessageTimeout) {
        this(subscription, mapper, nextMessageTimeout, null);
    }

    public NatsFlowSource(Subscription subscription,
                          java.util.function.Function<Message, T> mapper,
                          Duration nextMessageTimeout,
                          MeterRegistry meterRegistry) {
        if (subscription == null) {
            throw new IllegalArgumentException("subscription 非 null");
        }
        if (mapper == null) {
            throw new IllegalArgumentException("mapper 非 null");
        }
        this.subscription = subscription;
        this.mapper = mapper;
        this.nextMessageTimeout = nextMessageTimeout != null ? nextMessageTimeout : Duration.ofSeconds(1);
        this.meterRegistry = meterRegistry;

        if (meterRegistry != null) {
            Gauge.builder("app.template.source.nats.pending.messages", subscription,
                         s -> {
                             try { return s.getPendingMessageCount(); }
                             catch (Exception e) { return 0; }
                         })
                 .description("NATS 订阅中待处理的消息数")
                 .register(meterRegistry);
        }
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
            long start = System.currentTimeMillis();
            try {
                Message msg = subscription.nextMessage(nextMessageTimeout);
                if (meterRegistry != null) {
                    long elapsed = System.currentTimeMillis() - start;
                    Timer.builder("app.template.source.poll.duration")
                         .tag("sourceType", "nats")
                         .register(meterRegistry)
                         .record(elapsed, TimeUnit.MILLISECONDS);
                }
                if (msg != null) {
                    nextMessage = msg;
                    if (meterRegistry != null) {
                        Counter.builder("app.template.source.received")
                               .tag("sourceType", "nats")
                               .register(meterRegistry).increment();
                    }
                    return true;
                }
            } catch (IllegalStateException e) {
                closed = true;
                if (meterRegistry != null) {
                    Counter.builder("app.template.source.errors")
                           .tag("sourceType", "nats")
                           .tag("errorType", "IllegalStateException")
                           .register(meterRegistry).increment();
                }
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
        } catch (Exception e) {
            log.debug("NATS subscription unsubscribe failed, ignoring for best-effort release", e);
        }
    }
}
