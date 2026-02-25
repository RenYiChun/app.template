package com.lrenyi.template.core.metrics;

import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * 业务指标扩展工具类，封装 {@link MeterRegistry} 提供简洁的 counter/timer/gauge API。
 * <p>
 * 业务方注入此 Bean 后即可一行代码注册和记录自定义指标，无需直接操作 Micrometer API。
 * tags 参数为变长 key-value 对：{@code "key1", "value1", "key2", "value2"}。
 * <p>
 * 指标命名需遵循 {@code app.template.{module}.{name}} 规范。
 * 禁止使用高基数字段（userId/orderId/requestId 等）作为 tag。
 */
@Component
public class AppMetrics {
    private final MeterRegistry registry;

    public AppMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** 计数器 +1 */
    public void count(String name, String... tags) {
        Counter.builder(name).tags(tags).register(registry).increment();
    }

    /** 计数器 +N */
    public void count(String name, long amount, String... tags) {
        Counter.builder(name).tags(tags).register(registry).increment(amount);
    }

    /** 记录耗时（毫秒） */
    public void recordTime(String name, long durationMs, String... tags) {
        Timer.builder(name).tags(tags).register(registry)
             .record(durationMs, TimeUnit.MILLISECONDS);
    }

    /** 开始自动计时，返回 Sample 对象 */
    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    /** 停止计时并记录到指定指标 */
    public void stopTimer(Timer.Sample sample, String name, String... tags) {
        sample.stop(Timer.builder(name).tags(tags).register(registry));
    }

    /** 注册 Gauge（瞬时值，绑定到对象的数值函数） */
    public <T> void gauge(String name, T obj, ToDoubleFunction<T> valueFunc, String... tags) {
        Gauge.builder(name, obj, valueFunc).tags(tags).register(registry);
    }

    /** 获取底层 MeterRegistry（高级用法） */
    public MeterRegistry getRegistry() {
        return registry;
    }
}
