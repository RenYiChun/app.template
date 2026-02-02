package com.lrenyi.template.core.flow.manager;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.lrenyi.template.core.flow.FlowConstants;
import com.lrenyi.template.core.flow.context.FlowProgressSnapshot;
import com.lrenyi.template.core.flow.health.FlowHealth;
import com.lrenyi.template.core.flow.impl.FlowLauncher;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FlowProgressDisplay {
    private final FlowManager flowManager;
    private final ScheduledExecutorService scheduler;
    
    // 数字列宽度支持上亿（9 位）及以上展示
    private static final int W_JOB_ID = 16;
    private static final int W_STAT = 4;
    private static final int W_IN_TOTAL = 12;
    private static final int W_WAIT_Q = 10;
    private static final int W_ACTIVE_C = 10;
    private static final int W_STUCK = 10;
    private static final int W_SUCC = 12;
    private static final int W_LOSS = 12;
    private static final int W_CACHE = 24;
    private static final int W_TPS = 10;
    private static final int W_DURATION = 8;
    private static final int W_PROGRESS = 7;
    private static final int W_SUCCESS = 7;
    
    private static final String LINE = renderLine();
    private static final int TOTAL_W = LINE.length();
    
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
        // 启动时输出字段解析，帮助理解物理位移模型
        log.info("Starting Flow Progress Monitor...\n{}", getMetadataDescription());
        scheduler.scheduleAtFixedRate(() -> {
            try {
                displayStatus(null);
            } catch (Exception e) {
                log.error("Display error", e);
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
        
        int available = flowManager.getGlobalSemaphore().availablePermits();
        int limit = flowManager.getGlobalConfig().getGlobalSemaphoreMaxLimit();
        
        // 显示健康状态和详细信息
        com.lrenyi.template.core.flow.health.HealthStatus healthStatus = FlowHealth.checkHealth();
        Map<String, Object> healthDetails = FlowHealth.getHealthDetails();
        sb.append(String.format("[Health Status] %s%n", healthStatus.name()));
        
        // 输出详细健康指标
        renderHealthDetails(sb, healthDetails);
        
        sb.append(String.format("[JobGlobal Resource] Limit: %-5d | Available: %-5d | Active Jobs: %d%n",
                                limit,
                                available,
                                launchers.size()
        ));
        
        sb.append(LINE).append("\n");
        renderHeader(sb);
        sb.append(LINE);
        
        launchers.forEach((id, l) -> {
            if (targetJobId == null || id.equals(targetJobId)) {
                renderRow(sb, id, l);
            }
        });
        
        sb.append("\n").append(LINE);
        log.info(sb.toString());
        
        // 如果健康状态不是 HEALTHY，输出警告日志
        if (healthStatus != com.lrenyi.template.core.flow.health.HealthStatus.HEALTHY) {
            logHealthWarning(healthStatus, healthDetails);
        }
    }
    
    /**
     * 渲染健康检查详细信息
     */
    private void renderHealthDetails(StringBuilder sb, Map<String, Object> healthDetails) {
        @SuppressWarnings(
                "unchecked"
        ) java.util.List<Map<String, Object>> indicators = (java.util.List<Map<String, Object>>) healthDetails.get(
                "indicators");
        
        if (indicators != null && !indicators.isEmpty()) {
            for (Map<String, Object> indicator : indicators) {
                String name = (String) indicator.get("name");
                String status = (String) indicator.get("status");
                @SuppressWarnings("unchecked") Map<String, Object> details = (Map<String, Object>) indicator.get(
                        "details");
                
                sb.append(String.format("[Health Indicator: %s] Status: %s%n", name, status));
                
                if (details != null) {
                    // 输出关键指标
                    if (details.containsKey("semaphoreUsage")) {
                        Double usage = (Double) details.get("semaphoreUsage");
                        Integer used = (Integer) details.get("semaphoreUsed");
                        Integer maxLimit = (Integer) details.get("semaphoreMaxLimit");
                        sb.append(String.format("  - Semaphore Usage: %.2f%% (%d/%d)%n", usage * 100, used, maxLimit));
                    }
                    
                    if (details.containsKey("activeJobs")) {
                        Integer activeJobs = (Integer) details.get("activeJobs");
                        Integer activeLaunchers = (Integer) details.get("activeLaunchers");
                        sb.append(String.format("  - Active Jobs: %d, Active Launchers: %d%n",
                                                activeJobs,
                                                activeLaunchers
                        ));
                    }
                    
                    if (details.containsKey("resourceLeakDetected")) {
                        Boolean leakDetected = (Boolean) details.get("resourceLeakDetected");
                        if (Boolean.TRUE.equals(leakDetected)) {
                            sb.append("  - ⚠️  Resource Leak Detected!%n");
                        }
                    }
                    
                    if (details.containsKey("resourceRegistryInitialized")) {
                        Boolean initialized = (Boolean) details.get("resourceRegistryInitialized");
                        Boolean shutdown = (Boolean) details.get("resourceRegistryShutdown");
                        sb.append(String.format("  - Registry: Initialized=%s, Shutdown=%s%n", initialized, shutdown));
                    }
                }
            }
        }
    }
    
    /**
     * 输出健康警告日志
     */
    private void logHealthWarning(com.lrenyi.template.core.flow.health.HealthStatus status,
                                  Map<String, Object> healthDetails) {
        StringBuilder warning = new StringBuilder("⚠️  Flow Framework Health Warning: ");
        warning.append(status.name()).append("\n");
        
        @SuppressWarnings(
                "unchecked"
        ) java.util.List<Map<String, Object>> indicators = (java.util.List<Map<String, Object>>) healthDetails.get(
                "indicators");
        
        if (indicators != null) {
            for (Map<String, Object> indicator : indicators) {
                String indicatorStatus = (String) indicator.get("status");
                if (!"HEALTHY".equals(indicatorStatus)) {
                    warning.append("  - ")
                           .append(indicator.get("name"))
                           .append(": ")
                           .append(indicatorStatus)
                           .append("\n");
                    @SuppressWarnings("unchecked") Map<String, Object> details = (Map<String, Object>) indicator.get(
                            "details");
                    if (details != null) {
                        if (details.containsKey("semaphoreUsage")) {
                            Double usage = (Double) details.get("semaphoreUsage");
                            warning.append("    Semaphore Usage: ")
                                   .append(String.format("%.2f%%", usage * 100))
                                   .append("\n");
                        }
                        if (Boolean.TRUE.equals(details.get("resourceLeakDetected"))) {
                            warning.append("    ⚠️  Resource Leak Detected!\n");
                        }
                    }
                }
            }
        }
        
        if (status == com.lrenyi.template.core.flow.health.HealthStatus.UNHEALTHY) {
            log.error(warning.toString());
        } else {
            log.warn(warning.toString());
        }
    }
    
    private void renderHeader(StringBuilder sb) {
        sb.append("| ")
          .append(fix("JobId", W_JOB_ID))
          .append(" | ")
          .append(fix("Stat", W_STAT))
          .append(" | ")
          .append(fix("In(Total)", W_IN_TOTAL))
          .append(" | ")
          .append(fix("Wait(Q)", W_WAIT_Q))
          .append(" | ")
          .append(fix("Active(C)", W_ACTIVE_C))
          .append(" | ")
          .append(fix("Stuck(S)", W_STUCK))
          .append(" | ")
          .append(fix("Succ", W_SUCC))
          .append(" | ")
          .append(fix("Loss", W_LOSS))
          .append(" | ")
          .append(fix("Cache(Used/Cap)", W_CACHE))
          .append(" | ")
          .append(fix("TPS", W_TPS))
          .append(" | ")
          .append(fix("Duration", W_DURATION))
          .append(" | ")
          .append(fix("Progress", W_PROGRESS))
          .append(" | ")
          .append(fix("Success", W_SUCCESS))
          .append(" |\n");
    }
    
    private static String renderLine() {
        StringBuilder sb = new StringBuilder();
        sb.append("+ ")
          .append(fix("-", W_JOB_ID))
          .append(" + ")
          .append(fix("-", W_STAT))
          .append(" + ")
          .append(fix("-", W_IN_TOTAL))
          .append(" + ")
          .append(fix("-", W_WAIT_Q))
          .append(" + ")
          .append(fix("-", W_ACTIVE_C))
          .append(" + ")
          .append(fix("-", W_STUCK))
          .append(" + ")
          .append(fix("-", W_SUCC))
          .append(" + ")
          .append(fix("-", W_LOSS))
          .append(" + ")
          .append(fix("-", W_CACHE))
          .append(" + ")
          .append(fix("-", W_TPS))
          .append(" + ")
          .append(fix("-", W_DURATION))
          .append(" + ")
          .append(fix("-", W_PROGRESS))
          .append(" + ")
          .append(fix("-", W_SUCCESS))
          .append(" +");
        return sb.toString();
    }
    
    private void renderRow(StringBuilder sb, String id, FlowLauncher<?> launcher) {
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
          .append(fix(String.valueOf(s.getStuckCount()), W_STUCK))
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
          .append(" |");
    }
    
    private static String fix(String text, int width) {
        if (text == null) {
            text = "";
        }
        int visualWidth = 0;
        for (char c : text.toCharArray()) {
            visualWidth += (c > 127) ? 2 : 1;
        }
        int padding = width - visualWidth;
        if (padding <= 0) {
            return text.substring(0, Math.min(text.length(), width));
        }
        String repeat = " ";
        if (text.equalsIgnoreCase("-")) {
            repeat = "-";
        }
        return text + repeat.repeat(padding);
    }
    
    private String centerText() {
        int side = (TOTAL_W - "Flow Monitoring Dashboard (Physical Model)".length() - 2) / 2;
        return "=".repeat(Math.max(0, side)) + " " + "Flow Monitoring Dashboard (Physical Model)" + " " + "=".repeat(
                Math.max(0, TOTAL_W - side - "Flow Monitoring Dashboard (Physical Model)".length() - 2
        ));
    }
    
    private String formatDuration(long seconds) {
        long s = Math.max(0, seconds);
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
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
                                 (数字大，通常意味着 JobConfig 中的 jobProducerLimit 配小了，导致生产过快被挂起)
                2. 处理阶段:
                   - Active(C) : 当前正在处理中的活跃数据（占用物理许可）。
                   - Stuck(S)  : 滞留数。已离开缓存但尚未释放许可的数据（反映回调或清理压力）。
                   - Cache     : 缓存利用率 (当前存储数 / 最大容量)。
                3. 离场阶段:
                   - Succ      : 主动离场(Active Egress)。业务正常匹配完成的数量。
                   - Loss      : 被动离场(Passive Egress)。因超时、被动驱逐等导致的损耗。
                4. 指标质量:
                   - TPS       : 每秒“离场”数据量。即：每秒钟有多少条数据完成了处理（无论是 Succ 还是 Loss）。
                                 (它代表系统的净吞吐能力。如果 TPS 远低于进场速度，说明处理大厅出现了严重的堆积)
                   - Progress  : 总进度 ( (Succ + Loss) / 总预期量 )。
                   - Success   : 成功率 ( Succ / (Succ + Loss) )，衡量业务达成质量。
                ============================================================================================================""";
    }
}