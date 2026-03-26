package com.lrenyi.template.flow.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.Map;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import org.junit.jupiter.api.Test;

class FlowResourceHealthIndicatorTest {

    @Test
    void getDetailsUsesZeroWhenGlobalConsumerLimitDisabled() {
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
        flow.getLimits().getGlobal().setConsumerThreads(0);

        FlowResourceRegistry registry = mock(FlowResourceRegistry.class);
        FlowManager flowManager = mock(FlowManager.class);
        when(registry.isInitialized()).thenReturn(true);
        when(registry.isShutdown()).thenReturn(false);
        when(registry.getFlowConfig()).thenReturn(flow);
        when(registry.getGlobalSemaphore()).thenReturn(null);
        when(registry.getFlowConsumerExecutor()).thenReturn(mock(java.util.concurrent.ExecutorService.class));
        when(registry.getStorageEgressExecutor()).thenReturn(mock(java.util.concurrent.ScheduledExecutorService.class));
        when(flowManager.getActiveJobCount()).thenReturn(0);
        when(flowManager.getActiveLaunchers()).thenReturn(Map.of());

        FlowResourceHealthIndicator indicator = new FlowResourceHealthIndicator(registry, flowManager);

        Map<String, Object> details = indicator.getDetails();

        assertEquals(0, details.get("consumerThreadsLimit"));
        assertEquals(0, details.get("consumerThreadsAvailable"));
        assertEquals(0, details.get("consumerThreadsUsed"));
        assertEquals(0.0, details.get("consumerThreadsUsage"));
    }
}
