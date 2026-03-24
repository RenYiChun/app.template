package com.lrenyi.template.flow.pipeline;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.FlowTestSupport;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SinkJoiner} 在启用 {@code limits.global.sink-consumer-threads} 时通过全主机 Semaphore 限制终端回调并发。
 */
class SinkJoinerConcurrencyTest {

    @AfterEach
    void tearDown() {
        FlowTestSupport.cleanup();
        FlowResourceRegistry.reset();
    }

    @Test
    void globalSinkSemaphore_notCreatedWhenLimitDisabled() {
        FlowManager.reset();
        FlowResourceRegistry.reset();
        TemplateConfigProperties.Flow flow = baseFlow();
        flow.getLimits().getGlobal().setSinkConsumerThreads(0);
        FlowManager fm = FlowManager.getInstance(flow, new SimpleMeterRegistry());
        assertNull(fm.getResourceRegistry().getGlobalSinkSemaphore());
    }

    @Test
    void globalSinkSemaphore_createdWhenLimitPositive() {
        FlowManager.reset();
        FlowResourceRegistry.reset();
        TemplateConfigProperties.Flow flow = baseFlow();
        flow.getLimits().getGlobal().setSinkConsumerThreads(4);
        FlowManager fm = FlowManager.getInstance(flow, new SimpleMeterRegistry());
        assertNotNull(fm.getResourceRegistry().getGlobalSinkSemaphore());
        assertEquals(4, fm.getResourceRegistry().getGlobalSinkSemaphore().availablePermits());
    }

    @Test
    void sinkCallbacks_respectGlobalConcurrency() throws Exception {
        FlowManager.reset();
        FlowResourceRegistry.reset();
        TemplateConfigProperties.Flow flow = baseFlow();
        flow.getLimits().getGlobal().setSinkConsumerThreads(2);
        flow.setConsumerAcquireBlockingMode(TemplateConfigProperties.Flow.BackpressureBlockingMode.BLOCK_FOREVER);

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        FlowManager fm = FlowManager.getInstance(flow, meterRegistry);

        CountDownLatch twoInside = new CountDownLatch(2);
        CountDownLatch proceed = new CountDownLatch(1);

        SinkJoiner<Integer> sink = new SinkJoiner<>(Integer.class, (i, jobId) -> {
            twoInside.countDown();
            try {
                assertTrue(proceed.await(30, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, fm);

        ExecutorService pool = Executors.newFixedThreadPool(3);
        for (int i = 0; i < 3; i++) {
            pool.submit(() -> sink.onSingleConsumed(1, "job-sink", EgressReason.SINGLE_CONSUMED));
        }
        assertTrue(twoInside.await(10, TimeUnit.SECONDS), "应有 2 个线程进入 Sink 回调");
        proceed.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
    }

    @Test
    void withoutFlowManager_noExtraLimiting() {
        SinkJoiner<Integer> sink = new SinkJoiner<>(Integer.class, (i, jobId) -> { });
        sink.onSingleConsumed(1, "j", EgressReason.SINGLE_CONSUMED);
    }

    private static TemplateConfigProperties.Flow baseFlow() {
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
        flow.getLimits().getGlobal().setConsumerThreads(32);
        flow.getLimits().getPerJob().setStorageCapacity(1000);
        flow.getLimits().getPerJob().setProducerThreads(4);
        flow.getLimits().getPerJob().getKeyedCache().setCacheTtlMill(5000);
        return flow;
    }
}
