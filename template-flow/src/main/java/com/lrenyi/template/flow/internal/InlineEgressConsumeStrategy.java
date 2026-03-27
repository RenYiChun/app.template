package com.lrenyi.template.flow.internal;

import java.util.concurrent.TimeUnit;
import com.lrenyi.template.flow.context.FlowEntry;
import com.lrenyi.template.flow.exception.FlowExceptionHelper;
import com.lrenyi.template.flow.exception.FlowPhase;
import com.lrenyi.template.flow.metrics.FlowMetricNames;
import com.lrenyi.template.flow.metrics.FlowMetricTags;
import com.lrenyi.template.flow.model.EgressReason;
import com.lrenyi.template.flow.util.FlowLogHelper;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * egress worker 直接在当前线程执行消费逻辑。
 */
@Slf4j
public final class InlineEgressConsumeStrategy<T> implements EgressConsumeStrategy<T> {
    private final FlowFinalizer<T> finalizer;

    public InlineEgressConsumeStrategy(FlowFinalizer<T> finalizer) {
        this.finalizer = finalizer;
    }

    @Override
    public void submitSingle(FlowEntry<T> entry, FlowLauncher<?> launcher, EgressReason reason) {
        String jobId = entry.getJobId();
        long startTime = System.currentTimeMillis();
        final EgressReason finalReason = reason != null ? reason : EgressReason.SINGLE_CONSUMED;
        boolean didFinalize = false;
        try (entry) {
            if (entry.claimLogic()) {
                finalizer.egressHandler().performSingleConsumed(entry, finalReason);
                didFinalize = true;
            } else {
                log.debug("Entry {} claimed by other path, skipping inline finalizer",
                        FlowLogHelper.formatJobContext(entry.getJobId(), launcher.getMetricJobId()));
            }
        } catch (Exception t) {
            FlowExceptionHelper.handleException(jobId,
                    null,
                    t,
                    FlowPhase.FINALIZATION,
                    "inline_finalizer_body_failed",
                    launcher.getMetricJobId());
        } finally {
            if (didFinalize) {
                launcher.getTracker().onTerminated(1);
                long latency = System.currentTimeMillis() - startTime;
                Timer.builder(FlowMetricNames.FINALIZE_DURATION)
                        .tags(FlowMetricTags.resolve(jobId,
                                launcher.getMetricJobId(),
                                launcher.getTracker().getStageDisplayName()).toTags())
                        .register(finalizer.meterRegistry())
                        .record(latency, TimeUnit.MILLISECONDS);
            }
        }
    }
}
