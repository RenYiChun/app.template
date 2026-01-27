package com.lrenyi.template.core.flow.context;

import com.lrenyi.template.core.flow.manager.FlowCacheManager;
import com.lrenyi.template.core.flow.manager.FlowManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class FlowResourceContext {
    private final Semaphore globalSemaphore;
    private final FlowManager flowManager;
    private final FlowCacheManager flowCacheManger;
    private final ExecutorService globalExecutor;
}
