package com.lrenyi.template.flow.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicReference;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.api.FlowInlet;
import com.lrenyi.template.flow.api.FlowJoiner;
import com.lrenyi.template.flow.api.FlowPipeline;
import com.lrenyi.template.flow.api.FlowSource;
import com.lrenyi.template.flow.api.FlowSourceAdapters;
import com.lrenyi.template.flow.api.FlowSourceProvider;
import com.lrenyi.template.flow.api.EmbeddedBatchSpec;
import com.lrenyi.template.flow.api.ProgressTracker;
import com.lrenyi.template.flow.internal.DefaultProgressTracker;
import com.lrenyi.template.flow.internal.FlowInletImpl;
import com.lrenyi.template.flow.internal.FlowLauncher;
import com.lrenyi.template.flow.manager.FlowManager;
import lombok.extern.slf4j.Slf4j;

/**
 * FlowPipeline 核心实现。负责编排多个阶段及其生命周期。
 * 实现原理：
 * 1. 递归解析 StageDefinition 树。
 * 2. 从后向前（Sink 到 Source）创建 FlowLauncher。
 * 3. 通过 PipelineJoinerWrapper 实现阶段间的异步解耦与背压传递。
 *
 * @param <I> 管道初始输入的数据类型
 */
@Slf4j
public class FlowPipelineImpl<I> implements FlowPipeline<I> {
    private final String jobId;
    private final FlowManager flowManager;
    private final List<StageDefinition<?, ?>> stageDefinitions;
    private final PipelineProgressTracker pipelineTracker;
    private final List<FlowLauncher<?>> launchers = new ArrayList<>();
    private final AtomicReference<FlowInlet<I>> firstInlet = new AtomicReference<>();

    public FlowPipelineImpl(String jobId, FlowManager flowManager, List<StageDefinition<?, ?>> stages) {
        this.jobId = jobId;
        this.flowManager = flowManager;
        this.stageDefinitions = stages;
        this.pipelineTracker = new PipelineProgressTracker(jobId);
    }

    @Override
    public void run(FlowSource<I> source, TemplateConfigProperties.Flow flowConfig) {
        FlowInlet<I> inlet = startPush(flowConfig);
        try (FlowSourceProvider<I> provider = FlowSourceAdapters.singleSourceProvider(source)) {
            while (provider.hasNextSubSource()) {
                FlowSource<I> sub = provider.nextSubSource();
                try (sub) {
                    while (sub.hasNext()) {
                        inlet.push(sub.next());
                    }
                }
            }
            inlet.markSourceFinished();
        } catch (InterruptedException e) {
            log.warn("Pipeline run interrupted, jobId={}", jobId);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Pipeline run failed, jobId={}", jobId, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public FlowInlet<I> startPush(TemplateConfigProperties.Flow flowConfig) {
        FlowInlet<I> existing = firstInlet.get();
        if (existing != null) {
            return existing;
        }
        initializeStages(flowConfig);
        @SuppressWarnings("unchecked")
        FlowLauncher<I> firstLauncher = (FlowLauncher<I>) launchers.get(0);
        FlowInletImpl<I> inlet = new FlowInletImpl<>(firstLauncher);
        firstLauncher.setInFlightPushCountSupplier(inlet::getInFlightPushCount);
        firstInlet.set(inlet);
        return inlet;
    }

    @Override
    public ProgressTracker getProgressTracker() {
        return pipelineTracker;
    }

    @Override
    public void stop(boolean force) {
        for (FlowLauncher<?> launcher : launchers) {
            launcher.stop(force);
        }
    }

    private final ReentrantLock initLock = new ReentrantLock();

    private void initializeStages(TemplateConfigProperties.Flow flowConfig) {
        initLock.lock();
        try {
            if (!launchers.isEmpty()) {
                return;
            }
            buildStagesRecursive(jobId, stageDefinitions, flowConfig, null);
            if (!launchers.isEmpty()) {
                Collections.reverse(launchers);
                Collections.reverse(pipelineTracker.getTrackers());
            }
        } finally {
            initLock.unlock();
        }
    }

    /**
     * Micrometer {@code jobId} 标签：用管道级展示名替换冗长 UUID 前缀，后缀保留阶段序号与 fork 路径。
     * <p>请在首次 {@link #startPush} 之前对 {@link #getProgressTracker()} 调用 {@link ProgressTracker#setMetricJobId(String)}。</p>
     */
    private String stageMetricTag(String stageJobId) {
        String labelBase = pipelineTracker.getMetricJobId();
        if (stageJobId.startsWith(jobId)) {
            return labelBase + stageJobId.substring(jobId.length());
        }
        return stageJobId;
    }

    private FlowInlet<Object> buildStagesRecursive(String baseJobId, 
                                                   List<StageDefinition<?, ?>> defs, 
                                                   TemplateConfigProperties.Flow flowConfig,
                                                   FlowInlet<Object> nextInlet) {
        
        FlowInlet<Object> currentChainHead = nextInlet;
        
        for (int i = defs.size() - 1; i >= 0; i--) {
            StageDefinition<Object, Object> def = (StageDefinition<Object, Object>) defs.get(i);
            String stageJobId = baseJobId + ":" + i;
            
            if (def.isFork()) {
                List<FlowInlet<Object>> branchInlets = new ArrayList<>();
                for (int b = 0; b < def.branchStages().size(); b++) {
                    String branchJobId = stageJobId + ":fork:" + b;
                    branchInlets.add(buildStagesRecursive(branchJobId, def.branchStages().get(b), flowConfig, null));
                }
                
                final FlowInlet<Object> finalNext = currentChainHead;
                currentChainHead = new FlowInlet<Object>() {
                    @Override
                    public void push(Object item) {
                        log.info("Fork fanout triggered for jobId={}, branches={}", stageJobId, branchInlets.size());
                        for (FlowInlet<Object> head : branchInlets) {
                            head.push(item);
                        }
                        if (finalNext != null) {
                            finalNext.push(item);
                        }
                    }
                    @Override
                    public void markSourceFinished() {
                        for (FlowInlet<Object> head : branchInlets) {
                            head.markSourceFinished();
                        }
                        if (finalNext != null) {
                            finalNext.markSourceFinished();
                        }
                    }
                    @Override
                    public ProgressTracker getProgressTracker() { return null; }
                    @Override
                    public boolean isCompleted() { return false; }
                    @Override
                    public void stop(boolean force) {
                        for (FlowInlet<Object> head : branchInlets) {
                            head.stop(force);
                        }
                    }
                };
            } else {
                FlowJoiner<Object> userJoiner = def.joiner();
                if (userJoiner instanceof AggregationJoiner) {
                    ((AggregationJoiner<Object>) userJoiner).setScheduler(
                        flowManager.getResourceRegistry().getStorageEgressExecutor());
                }

                EmbeddedBatchSpec embeddedBatch = def.embeddedBatch();
                PipelineJoinerWrapper<Object, Object> wrapper = new PipelineJoinerWrapper<>(
                        userJoiner, def.dispatch(), embeddedBatch);
                if (embeddedBatch != null) {
                    wrapper.setSchedulerForEmbeddedBatch(
                            flowManager.getResourceRegistry().getStorageEgressExecutor());
                }
                if (currentChainHead != null) {
                    final FlowInlet<Object> nextInletRef = currentChainHead;
                    wrapper.addDownstream((obj) -> nextInletRef.push(obj));
                }

                DefaultProgressTracker tracker = new DefaultProgressTracker(stageJobId, flowManager);
                String metricTag = stageMetricTag(stageJobId);
                tracker.setMetricJobId(metricTag);
                FlowLauncher<Object> launcher =
                        flowManager.createLauncher(stageJobId, metricTag, wrapper, tracker, flowConfig);

                FlowInlet<Object> inlet = new FlowInletImpl<>(launcher);
                if (currentChainHead != null) {
                    final FlowInlet<Object> nextInletRef = currentChainHead;
                    if (embeddedBatch != null) {
                        tracker.getCompletionFuture().thenRun(() -> {
                            wrapper.flushEmbeddedBatchOnUpstreamComplete();
                            nextInletRef.markSourceFinished();
                        });
                    } else {
                        tracker.getCompletionFuture().thenRun(nextInletRef::markSourceFinished);
                    }
                }
                
                pipelineTracker.addTracker(tracker);
                launchers.add(launcher);
                currentChainHead = inlet;
            }
        }
        return currentChainHead;
    }
}
