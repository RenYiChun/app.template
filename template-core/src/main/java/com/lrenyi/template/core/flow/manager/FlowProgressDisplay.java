package com.lrenyi.template.core.flow.manager;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.lrenyi.template.core.flow.display.FlowHealthDetailsRenderer;
import com.lrenyi.template.core.flow.display.FlowProgressRowRenderer;
import com.lrenyi.template.core.flow.display.FlowProgressTableLayout;
import com.lrenyi.template.core.flow.health.FlowHealth;
import com.lrenyi.template.core.flow.internal.FlowLauncher;
import com.lrenyi.template.core.flow.model.FlowConstants;
import lombok.extern.slf4j.Slf4j;

import static com.lrenyi.template.core.flow.display.FlowProgressTableLayout.LINE;
import static com.lrenyi.template.core.flow.display.FlowProgressTableLayout.TOTAL_W;

@Slf4j
public class FlowProgressDisplay {
    private final FlowManager flowManager;
    private final ScheduledExecutorService scheduler;

    public FlowProgressDisplay(FlowManager flowManager) {
        this.flowManager = flowManager;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, FlowConstants.THREAD_NAME_PROGRESS_DISPLAY);
            t.setDaemon(true);
            return t;
        });
    }

    public void start(long initialDelay, long period, TimeUnit unit) {
        if (period <= 0) {
            return;
        }
        log.info("Starting Flow Progress Monitor...\n{}", getMetadataDescription());
        scheduler.scheduleAtFixedRate(() -> {
            try {
                displayStatus(null);
            } catch (Exception e) {
                log.error("Flow progress display failed", e);
            }
        }, initialDelay, period, unit);
    }

    public void displayStatus(String targetJobId) {
        Map<String, FlowLauncher<?>> launchers = flowManager.getActiveLaunchers();
        if (launchers.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder("\n");
        sb.append(centerText()).append("\n");

        var healthStatus = FlowHealth.checkHealth();
        Map<String, Object> healthDetails = FlowHealth.getHealthDetails();
        sb.append(String.format("[Health Status] %s%n", healthStatus.name()));
        FlowHealthDetailsRenderer.render(sb, healthDetails);

        sb.append(LINE).append("\n");
        FlowProgressTableLayout.renderHeader(sb);
        sb.append(LINE);

        launchers.forEach((id, l) -> {
            if (targetJobId == null || id.equals(targetJobId)) {
                FlowProgressRowRenderer.render(sb, id, l);
            }
        });

        sb.append("\n").append(LINE);
        log.info(sb.toString());
    }

    private String centerText() {
        String title = "Flow Monitoring Dashboard (Physical Model)";
        int side = (TOTAL_W - title.length() - 2) / 2;
        return "=".repeat(Math.max(0, side)) + " " + title + " "
                + "=".repeat(Math.max(0, TOTAL_W - side - title.length() - 2));
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private String getMetadataDescription() {
        return """
                ============================================================================================================
                [审计流量监控字段解析]
                ------------------------------------------------------------------------------------------------------------
                1. 进场阶段:
                   - In(Total) : 累计已进入流水线的数据总量。
                   - Wait(Q)   : 挂起等待数。数据已到达但尚未进入缓存。
                                 (数字大，通常意味着 Flow 配置中的 parallelism 配小了，导致生产过快被挂起)
                2. 处理阶段:
                   - Active(C) : 当前正在处理中的活跃数据（占用物理许可）。
                   - Cache     : 缓存利用率 (当前存储数 / 最大容量)。
                   - Pending   : 已离库未终结数。已从缓存驱逐/配对离库并提交到消费管道，在等待或占用消费许可，尚未 onGlobalTerminated。
                3. 离场阶段:
                   - Succ      : 主动离场(Active Egress)。业务正常匹配完成的数量。
                   - Loss      : 被动离场(Passive Egress)。因超时、被动驱逐等导致的损耗。
                4. 指标质量:
                   - TPS       : 每秒"离场"数据量。即：每秒钟有多少条数据完成了处理（无论是 Succ 还是 Loss）。
                                 (它代表系统的净吞吐能力。如果 TPS 远低于进场速度，说明处理大厅出现了严重的堆积)
                   - Progress  : 总进度 ( (Succ + Loss) / 总预期量 )。
                   - Success   : 成功率 ( Succ / (Succ + Loss) )，衡量业务达成质量。
                ============================================================================================================""";
    }
}
