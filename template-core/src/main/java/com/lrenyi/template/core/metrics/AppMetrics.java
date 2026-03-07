package com.lrenyi.template.core.metrics;

import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;
import java.util.function.ToDoubleFunction;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * 业务指标扩展工具类，封装 {@link MeterRegistry} 提供简洁的 counter/timer/gauge/summary/longTaskTimer API。
 * <p>
 * 业务方注入此 Bean 后即可一行代码注册和记录自定义指标，无需直接操作 Micrometer API。
 * tags 参数为变长 key-value 对：{@code "key1", "value1", "key2", "value2"}。
 * <p>
 * 指标命名需遵循 {@code app.template.{module}.{name}} 规范。
 * 禁止使用高基数字段（userId/orderId/requestId 等）作为 tag。
 */
public record AppMetrics(MeterRegistry registry) {
    
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
        recordTime(name, durationMs, TimeUnit.MILLISECONDS, tags);
    }
    
    /** 记录耗时（指定时间单位） */
    public void recordTime(String name, long duration, TimeUnit unit, String... tags) {
        Timer.builder(name).tags(tags).register(registry).record(duration, unit);
    }
    
    /** 开始自动计时，返回 Sample，完成后调用 {@link #stopTimer} */
    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }
    
    /** 停止计时并记录到指定指标 */
    public void stopTimer(Timer.Sample sample, String name, String... tags) {
        sample.stop(Timer.builder(name).tags(tags).register(registry));
    }
    
    /** 分布摘要：记录非时间数值（如请求体大小、批处理条数），用于统计分布与分位数 */
    public void summary(String name, double amount, String... tags) {
        DistributionSummary.builder(name).tags(tags).register(registry).record(amount);
    }
    
    /** 开始长任务计时，返回 Sample，任务完成后调用 {@link LongTaskTimer.Sample#stop()} */
    public LongTaskTimer.Sample startLongTaskTimer(String name, String... tags) {
        return LongTaskTimer.builder(name).tags(tags).register(registry).start();
    }
    
    /** 注册 Gauge（瞬时值，绑定到对象的数值函数） */
    public <T> void gauge(String name, T obj, ToDoubleFunction<T> valueFunc, String... tags) {
        Gauge.builder(name, obj, valueFunc).tags(tags).register(registry);
    }
    
    /** 注册 Gauge（瞬时值，无状态 Supplier，适用于简单数值如队列大小、内存占用） */
    public void gauge(String name, DoubleSupplier supplier, String... tags) {
        Gauge.builder(name, supplier::getAsDouble).tags(tags).register(registry);
    }
}
