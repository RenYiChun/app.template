package com.lrenyi.template.core.flow.display;

import com.lrenyi.template.core.flow.context.FlowProgressSnapshot;
import com.lrenyi.template.core.flow.internal.FlowLauncher;

import static com.lrenyi.template.core.flow.display.FlowProgressTableLayout.W_ACTIVE_C;
import static com.lrenyi.template.core.flow.display.FlowProgressTableLayout.W_CACHE;
import static com.lrenyi.template.core.flow.display.FlowProgressTableLayout.W_DURATION;
import static com.lrenyi.template.core.flow.display.FlowProgressTableLayout.W_JOB_ID;
import static com.lrenyi.template.core.flow.display.FlowProgressTableLayout.W_LOSS;
import static com.lrenyi.template.core.flow.display.FlowProgressTableLayout.W_PROGRESS;
import static com.lrenyi.template.core.flow.display.FlowProgressTableLayout.W_STAT;
import static com.lrenyi.template.core.flow.display.FlowProgressTableLayout.W_SUCCESS;
import static com.lrenyi.template.core.flow.display.FlowProgressTableLayout.W_PENDING;
import static com.lrenyi.template.core.flow.display.FlowProgressTableLayout.W_SUCC;
import static com.lrenyi.template.core.flow.display.FlowProgressTableLayout.W_TPS;
import static com.lrenyi.template.core.flow.display.FlowProgressTableLayout.W_WAIT_Q;
import static com.lrenyi.template.core.flow.display.FlowProgressTableLayout.fix;
import static com.lrenyi.template.core.flow.display.FlowProgressTableLayout.formatDuration;

/**
 * 渲染 Flow 进度表格单行数据的工具类。
 */
public final class FlowProgressRowRenderer {

    private static final int W_IN_TOTAL = FlowProgressTableLayout.W_IN_TOTAL;

    private FlowProgressRowRenderer() {
    }

    public static void render(StringBuilder sb, String id, FlowLauncher<?> launcher) {
        FlowProgressSnapshot s = launcher.getTaskOrchestrator().tracker().getSnapshot();
        String status = s.endTimeMillis() > 0 ? "DONE" : "RUN";
        String cacheInfo = s.inStorage() + "/" + launcher.getCacheCapacity();
        long durationMs =
                (s.endTimeMillis() > 0 ? s.endTimeMillis() : System.currentTimeMillis()) - s.startTimeMillis();

        sb.append("\n| ")
                .append(fix(id, W_JOB_ID))
                .append(" | ")
                .append(fix(status, W_STAT))
                .append(" | ")
                .append(fix(String.valueOf(s.productionAcquired()), W_IN_TOTAL))
                .append(" | ")
                .append(fix(String.valueOf(s.getInProductionCount()), W_WAIT_Q))
                .append(" | ")
                .append(fix(String.valueOf(s.activeConsumers()), W_ACTIVE_C))
                .append(" | ")
                .append(fix(String.valueOf(s.activeEgress()), W_SUCC))
                .append(" | ")
                .append(fix(String.valueOf(s.passiveEgress()), W_LOSS))
                .append(" | ")
                .append(fix(cacheInfo, W_CACHE))
                .append(" | ")
                .append(fix(String.format("%.1f", s.getTps()), W_TPS))
                .append(" | ")
                .append(fix(formatDuration(durationMs / 1000), W_DURATION))
                .append(" | ")
                .append(fix(String.format("%.1f%%", s.getCompletionRate() * 100), W_PROGRESS))
                .append(" | ")
                .append(fix(String.format("%.1f%%", s.getSuccessRate() * 100), W_SUCCESS))
                .append(" | ")
                .append(fix(String.valueOf(s.getPendingConsumerCount()), W_PENDING))
                .append(" |");
    }
}
