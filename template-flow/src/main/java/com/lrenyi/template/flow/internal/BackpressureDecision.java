package com.lrenyi.template.flow.internal;

/**
 * 背压决策结果。
 */
public final class BackpressureDecision {
    public enum Type {
        /** 允许立即继续。 */
        PROCEED,
        /** 需要阻塞等待一段时间后重试。 */
        BLOCK
    }

    private final Type type;
    private final long waitMs;
    private final String reason;

    public BackpressureDecision(Type type, long waitMs, String reason) {
        this.type = type;
        this.waitMs = waitMs;
        this.reason = reason;
    }

    public Type getType() {
        return type;
    }

    public long getWaitMs() {
        return waitMs;
    }

    public String getReason() {
        return reason;
    }

    public static BackpressureDecision proceed() {
        return new BackpressureDecision(Type.PROCEED, 0L, "ok");
    }

    public static BackpressureDecision block(long waitMs, String reason) {
        return new BackpressureDecision(Type.BLOCK, waitMs, reason);
    }
}

