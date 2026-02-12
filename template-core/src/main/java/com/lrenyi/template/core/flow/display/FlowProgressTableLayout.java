package com.lrenyi.template.core.flow.display;

/**
 * Flow 进度展示的表格布局定义，包含列宽与格式化工具。
 */
public final class FlowProgressTableLayout {
    // 数字列宽度支持上亿（9 位）及以上展示
    public static final int W_JOB_ID = 16;
    public static final int W_STAT = 4;
    public static final int W_IN_TOTAL = 12;
    public static final int W_WAIT_Q = 10;
    public static final int W_ACTIVE_C = 10;
    public static final int W_SUCC = 12;
    public static final int W_LOSS = 12;
    public static final int W_CACHE = 24;
    public static final int W_TPS = 10;
    public static final int W_DURATION = 8;
    public static final int W_PROGRESS = 7;
    public static final int W_SUCCESS = 7;

    public static final String LINE = renderLine();
    public static final int TOTAL_W = LINE.length();

    private FlowProgressTableLayout() {
    }

    public static String renderLine() {
        return "+ "
                + fix("-", W_JOB_ID)
                + " + "
                + fix("-", W_STAT)
                + " + "
                + fix("-", W_IN_TOTAL)
                + " + "
                + fix("-", W_WAIT_Q)
                + " + "
                + fix("-", W_ACTIVE_C)
                + " + "
                + fix("-", W_SUCC)
                + " + "
                + fix("-", W_LOSS)
                + " + "
                + fix("-", W_CACHE)
                + " + "
                + fix("-", W_TPS)
                + " + "
                + fix("-", W_DURATION)
                + " + "
                + fix("-", W_PROGRESS)
                + " + "
                + fix("-", W_SUCCESS)
                + " +";
    }

    public static void renderHeader(StringBuilder sb) {
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

    /**
     * 按指定宽度格式化文本，支持中英文混合时视觉宽度对齐。
     */
    public static String fix(String text, int width) {
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

    public static String formatDuration(long seconds) {
        long s = Math.max(0, seconds);
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }
}
