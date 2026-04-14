package com.lrenyi.template.flow.storage;

import com.lrenyi.template.core.TemplateConfigProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultDeferredExpiryPolicyTest {
    
    @Test
    void firstCallUsesInitialDelay() {
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
        TemplateConfigProperties.Flow.PerJob perJob = flow.getLimits().getPerJob();
        perJob.getKeyedCache().setExpiryDeferInitialMill(100);
        perJob.getKeyedCache().setExpiryDeferMaxMill(1_000);
        
        DefaultDeferredExpiryPolicy policy = new DefaultDeferredExpiryPolicy(perJob);
        long now = 1_000L;
        long next = policy.nextCheckAt(now, 0, 0, 0);
        
        assertEquals(now + 100, next);
    }
    
    @Test
    void subsequentCallsExponentialBackoffAndCappedByMax() {
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
        TemplateConfigProperties.Flow.PerJob perJob = flow.getLimits().getPerJob();
        perJob.getKeyedCache().setExpiryDeferInitialMill(100);
        perJob.getKeyedCache().setExpiryDeferMaxMill(1_000);
        
        DefaultDeferredExpiryPolicy policy = new DefaultDeferredExpiryPolicy(perJob);
        long now = 10_000L;
        
        long first = policy.nextCheckAt(now, 0, 0, 0);
        long second = policy.nextCheckAt(now, 0, 0, first);
        long third = policy.nextCheckAt(now, 0, 0, second);
        long fourth = policy.nextCheckAt(now, 0, 0, third);
        
        assertEquals(now + 100, first);
        assertEquals(now + 200, second);
        assertEquals(now + 400, third);
        assertEquals(now + 800, fourth);
        // 800*2=1600 超过 max 1000，第五步才封顶
        long fifth = policy.nextCheckAt(now, 0, 0, fourth);
        assertEquals(now + 1_000, fifth);
    }
    
    @Test
    void nextCheckAtDoesNotExceedHardExpire() {
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
        TemplateConfigProperties.Flow.PerJob perJob = flow.getLimits().getPerJob();
        perJob.getKeyedCache().setExpiryDeferInitialMill(500);
        perJob.getKeyedCache().setExpiryDeferMaxMill(5_000);
        
        DefaultDeferredExpiryPolicy policy = new DefaultDeferredExpiryPolicy(perJob);
        long now = 100_000L;
        long hardExpireAt = now + 300;
        
        long next = policy.nextCheckAt(now, 0, hardExpireAt, 0);
        
        assertTrue(next <= hardExpireAt, "nextCheckAt 应不晚于硬超时时间");
    }
}

