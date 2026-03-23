package com.lrenyi.template.flow.manager;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.health.FlowHealth;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowManagerTest {
    
    private TemplateConfigProperties.Flow config;
    
    @BeforeEach
    void setUp() {
        FlowManager.reset();
        FlowResourceRegistry.reset();
        FlowHealth.clearIndicators();
        config = new TemplateConfigProperties.Flow();
        config.getLimits().getGlobal().setConsumerThreads(100);
    }
    
    @AfterEach
    void tearDown() {
        FlowManager.reset();
        FlowResourceRegistry.reset();
        FlowHealth.clearIndicators();
    }
    
    @Test
    void getInstanceReturnsNonNull() {
        FlowManager manager = FlowManager.getInstance(config);
        assertNotNull(manager);
    }
    
    @Test
    void getInstanceSameConfigReturnsSameInstance() {
        FlowManager m1 = FlowManager.getInstance(config);
        FlowManager m2 = FlowManager.getInstance(config);
        assertSame(m1, m2);
    }
    
    @Test
    void getInstanceDifferentConfigRecreatesInstance() {
        FlowManager m1 = FlowManager.getInstance(config);
        TemplateConfigProperties.Flow config2 = new TemplateConfigProperties.Flow();
        config2.getLimits().getGlobal().setConsumerThreads(200);
        FlowManager m2 = FlowManager.getInstance(config2);
        assertNotNull(m2);
        assertNotSame(m1, m2);
    }

    @Test
    void getInstanceDifferentTimeoutAlsoRecreatesInstance() {
        FlowManager m1 = FlowManager.getInstance(config);
        TemplateConfigProperties.Flow config2 = new TemplateConfigProperties.Flow();
        config2.getLimits().getGlobal().setConsumerThreads(100);
        config2.setConsumerAcquireTimeoutMill(1234);

        FlowManager m2 = FlowManager.getInstance(config2);

        assertNotSame(m1, m2);
    }

    @Test
    void getInstanceRebindsFromFallbackRegistryToApplicationRegistry() {
        FlowManager fallbackManager = FlowManager.getInstance(config);
        CompositeMeterRegistry applicationRegistry = new CompositeMeterRegistry();

        FlowManager rebound = FlowManager.getInstance(config, applicationRegistry);

        assertNotSame(fallbackManager, rebound);
        assertSame(applicationRegistry, rebound.getMeterRegistry());
    }
    
    @Test
    void packageConstructorCreatesInstanceWithoutInit() {
        FlowManager manager = new FlowManager(config, new SimpleMeterRegistry(), true);
        assertNotNull(manager);
        assertNotNull(manager.getResourceRegistry());
        assertNotNull(manager.getActiveLaunchers());
    }
    
    @Test
    void getActiveLaunchersInitiallyEmpty() {
        FlowManager manager = FlowManager.getInstance(config);
        Map<String, ?> launchers = manager.getActiveLaunchers();
        assertNotNull(launchers);
        assertTrue(launchers.isEmpty());
    }
    
    @Test
    void getHealthStatusReturnsMap() {
        FlowManager manager = FlowManager.getInstance(config);
        Map<String, Object> health = manager.getHealthStatus();
        assertNotNull(health);
    }
    
    @Test
    void resetClearsInstance() {
        FlowManager.getInstance(config);
        FlowManager.reset();
        FlowManager m2 = FlowManager.getInstance(config);
        assertNotNull(m2);
    }

    @Test
    void createLauncherRejectsConcurrentDuplicateJobId() throws Exception {
        FlowLauncher<Object> launcher = mock(FlowLauncher.class);
        FlowJoiner<Object> joiner = mock(FlowJoiner.class);
        ProgressTracker tracker1 = mock(ProgressTracker.class);
        ProgressTracker tracker2 = mock(ProgressTracker.class);
        CountDownLatch enteredFactory = new CountDownLatch(1);
        CountDownLatch releaseFactory = new CountDownLatch(1);
        when(launcher.getJobId()).thenReturn("job-1");
        when(launcher.getTracker()).thenReturn(tracker1);

        FlowManager manager = new FlowManager(config, new SimpleMeterRegistry(), true) {
            @Override
            <T> FlowLauncher<T> buildLauncher(String jobId,
                    String metricJobId,
                    FlowJoiner<T> flowJoiner,
                    ProgressTracker tracker,
                    TemplateConfigProperties.Flow flowConfig) {
                enteredFactory.countDown();
                try {
                    releaseFactory.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                @SuppressWarnings("unchecked")
                FlowLauncher<T> casted = (FlowLauncher<T>) launcher;
                return casted;
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<FlowLauncher<Object>> first = executor.submit(() ->
                    manager.createLauncher("job-1", joiner, tracker1, config));
            enteredFactory.await();
            Future<Object> second = executor.submit(() -> {
                try {
                    return manager.createLauncher("job-1", joiner, tracker2, config);
                } catch (Exception ex) {
                    return ex;
                }
            });
            releaseFactory.countDown();

            assertSame(launcher, first.get());
            Object secondResult = second.get();
            assertInstanceOf(IllegalStateException.class, secondResult);
            assertEquals("Job job-1 未结束，不能重复创建。请先对该 job 执行 stop 后再启动新任务。",
                    ((IllegalStateException) secondResult).getMessage());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void createLauncherAllowsReplacingStoppedJobAndIgnoresStaleUnregister() {
        FlowLauncher<Object> launcher1 = mock(FlowLauncher.class);
        FlowLauncher<Object> launcher2 = mock(FlowLauncher.class);
        FlowJoiner<Object> joiner = mock(FlowJoiner.class);
        ProgressTracker tracker1 = mock(ProgressTracker.class);
        ProgressTracker tracker2 = mock(ProgressTracker.class);
        when(launcher1.getJobId()).thenReturn("job-1");
        when(launcher1.getTracker()).thenReturn(tracker1);
        when(launcher1.isStopped()).thenReturn(true);
        when(launcher2.getJobId()).thenReturn("job-1");
        when(launcher2.getTracker()).thenReturn(tracker2);

        FlowManager manager = new FlowManager(config, new SimpleMeterRegistry(), true) {
            @Override
            <T> FlowLauncher<T> buildLauncher(String jobId,
                    String metricJobId,
                    FlowJoiner<T> flowJoiner,
                    ProgressTracker tracker,
                    TemplateConfigProperties.Flow flowConfig) {
                @SuppressWarnings("unchecked")
                FlowLauncher<T> launcher = (FlowLauncher<T>) (tracker == tracker1 ? launcher1 : launcher2);
                return launcher;
            }
        };

        FlowLauncher<Object> first = manager.createLauncher("job-1", joiner, tracker1, config);
        long firstGeneration = manager.currentGeneration("job-1");
        FlowLauncher<Object> second = manager.createLauncher("job-1", joiner, tracker2, config);

        assertSame(launcher1, first);
        assertSame(launcher2, second);
        assertSame(launcher2, manager.getActiveLauncher("job-1"));
        assertFalse(manager.unregisterIfGenerationMatches("job-1", firstGeneration));
        assertSame(launcher2, manager.getActiveLauncher("job-1"));
    }
}
