package com.lrenyi.template.core.flow.manager;

import com.lrenyi.template.core.flow.context.FlowProgressSnapshot;
import com.lrenyi.template.core.flow.impl.FlowLauncher;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FlowProgressDisplay {
    private final FlowManager flowManager;
    private final ScheduledExecutorService scheduler;
    
    // 延伸了分隔线以容纳新增的列，总宽度调整为 144
    private static final String LINE =
            "+------------------+------+----------+----------+----------+----------+-------+-------+------------------+----------+----------+---------+---------+";
    private static final int TOTAL_W = LINE.length();
    
    public FlowProgressDisplay(FlowManager flowManager) {
        this.flowManager = flowManager;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "flow-progress-display");
            t.setDaemon(true);
            return t;
        });
    }
    
    public void start(long initialDelay, long period, TimeUnit unit) {
        // 启动时输出字段解析，帮助理解物理位移模型
        log.info("Starting Flow Progress Monitor...\n{}", getMetadataDescription());
        scheduler.scheduleAtFixedRate(() -> {
            try {displayStatus(null);} catch (Exception e) {log.error("Display error", e);}
        }, initialDelay, period, unit);
    }
    
    public void displayStatus(String targetJobId) {
        Map<String, FlowLauncher<?>> launchers = flowManager.getActiveLaunchers();
        if (launchers.isEmpty()) {return;}
        
        StringBuilder sb = new StringBuilder("\n");
        sb.append(centerText("Flow Monitoring Dashboard (Physical Model)")).append("\n");
        
        int available = flowManager.getGlobalSemaphore().availablePermits();
        int limit = flowManager.getGlobalConfig().getGlobalSemaphoreMaxLimit();
        sb.append(String.format("[JobGlobal Resource] Limit: %-5d | Available: %-5d | Active Jobs: %d%n",
                                limit,
                                available,
                                launchers.size()
        ));
        
        sb.append(LINE).append("\n");
        // 修改表头：将原本的 Yield 拆分为 Progress(完成率) 和 Success(成功率)
        sb.append("| JobId            | Stat | In(Total)| Wait(Q)  | Active(C)| Stuck(S) | Succ  | Loss  | Cache" +
                          "(Used/Cap)  | TPS      | Duration | Progress| Success |\n");
        sb.append(LINE);
        
        launchers.forEach((id, l) -> {
            if (targetJobId == null || id.equals(targetJobId)) {
                renderRow(sb, id, l);
            }
        });
        
        sb.append("\n").append(LINE);
        log.info(sb.toString());
    }
    
    private void renderRow(StringBuilder sb, String id, FlowLauncher<?> launcher) {
        FlowProgressSnapshot s = launcher.getTaskOrchestrator().getTracker().getSnapshot();
        String status = s.endTimeMillis() > 0 ? "DONE" : "RUN";
        String cacheInfo = s.inStorage() + "/" + launcher.getCacheCapacity();
        long durationMs =
                (s.endTimeMillis() > 0 ? s.endTimeMillis() : System.currentTimeMillis()) - s.startTimeMillis();
        
        sb.append("\n| ")
          .append(fix(id, 16))
          .append(" | ")
          .append(fix(status, 4))
          .append(" | ")
          .append(fix(String.valueOf(s.productionAcquired()), 8))
          .append(" | ")
          .append(fix(String.valueOf(s.getInProductionCount()), 8))
          .append(" | ")
          .append(fix(String.valueOf(s.activeConsumers()), 8))
          .append(" | ")
          .append(fix(String.valueOf(s.getStuckCount()), 8))
          .append(" | ")
          .append(fix(String.valueOf(s.activeEgress()), 5))
          .append(" | ")
          .append(fix(String.valueOf(s.passiveEgress()), 5))
          .append(" | ")
          .append(fix(cacheInfo, 16))
          .append(" | ")
          .append(fix(String.format("%.1f", s.getTps()), 8))
          .append(" | ")
          .append(fix(formatDuration(durationMs / 1000), 8))
          .append(" | ")
          // 对应 getCompletionRate (物理处理了多少)
          .append(fix(String.format("%.1f%%", s.getCompletionRate() * 100), 7))
          .append(" | ")
          // 对应 getSuccessRate (处理完的里成了多少)
          .append(fix(String.format("%.1f%%", s.getSuccessRate() * 100), 7))
          .append(" |");
    }
    
    private String fix(String text, int width) {
        if (text == null) {text = "";}
        int visualWidth = 0;
        for (char c : text.toCharArray()) {
            visualWidth += (c > 127) ? 2 : 1;
        }
        int padding = width - visualWidth;
        if (padding <= 0) {return text.substring(0, Math.min(text.length(), width));}
        return text + " ".repeat(padding);
    }
    
    private String centerText(String title) {
        int side = (TOTAL_W - title.length() - 2) / 2;
        return "=".repeat(Math.max(0, side)) + " " + title + " " + "=".repeat(Math.max(0,
                                                                                       TOTAL_W - side - title.length() - 2
        ));
    }
    
    private String formatDuration(long seconds) {
        long s = Math.max(0, seconds);
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }
    
    public void stop() {
        if (scheduler != null) {scheduler.shutdown();}
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