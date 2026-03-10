package com.lrenyi.template.flow.internal;

/**
 * 背压策略接口：根据当前快照给出等待/放行决策。
 */
public interface BackpressurePolicy {

    BackpressureDecision decide(BackpressureSnapshot snapshot);

}

