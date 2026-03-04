package com.lrenyi.template.flow.it;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.FlowTestSupport;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.engine.FlowJoinerEngine;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.model.FlowStorageType;
import com.lrenyi.template.flow.resource.FlowResourceRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MustMatchRetryConstraintTest {
    
    @AfterEach
    void tearDown() {
        FlowManager.reset();
        FlowResourceRegistry.reset();
    }
    
    @Test
    void mustMatchRetryWithQueueShouldFail() {
        TemplateConfigProperties.Flow global = FlowTestSupport.defaultFlowConfig();
        FlowManager manager = FlowTestSupport.createManager(global);
        FlowJoinerEngine engine = new FlowJoinerEngine(manager);
        
        TemplateConfigProperties.Flow jobFlow = new TemplateConfigProperties.Flow();
        jobFlow.getLimits().getPerJob().setMustMatchRetryEnabled(true);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> engine.startPush(
                "job-must-match-retry-queue",
                new QueueMatchedJoiner(),
                jobFlow
        ));
        assertTrue(exception.getMessage().contains("must-match-retry"));
    }
    
    private static class QueueMatchedJoiner implements FlowJoiner<String> {
        @Override
        public FlowStorageType getStorageType() {
            return FlowStorageType.QUEUE;
        }
        
        @Override
        public Class<String> getDataType() {
            return String.class;
        }
        
        @Override
        public FlowSourceProvider<String> sourceProvider() {
            return FlowSourceAdapters.emptyProvider();
        }
        
        @Override
        public String joinKey(String item) {
            return item;
        }
        
        @Override
        public void onSuccess(String existing, String incoming, String jobId) {
        }
        
        @Override
        public boolean needMatched() {
            return true;
        }
        
        @Override
        public boolean isRetryable(String item, String jobId) {
            return true;
        }
        
        @Override
        public void onFailed(String item, String jobId) {
        }
    }
}
