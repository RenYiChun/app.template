package com.lrenyi.template.flow.model;

/**
 * Storage egress 后进入消费逻辑的执行模式。
 */
public enum FlowConsumeExecutionMode {
    /**
     * 默认模式：egress 后通过 finalizer 异步提交到 consumer executor。
     */
    ASYNC,
    /**
     * 内联模式：egress worker 直接在当前线程执行消费逻辑。
     */
    INLINE
}
