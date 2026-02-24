package com.lrenyi.template.core.flow.model;

import java.util.concurrent.TimeUnit;

/**
 * Flow 框架常量定义
 * 集中管理所有配置常量和默认值
 */
public final class FlowConstants {

    private FlowConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ========== 资源关闭超时 ==========

    /**
     * 默认资源关闭超时时间（秒）
     */
    public static final long DEFAULT_SHUTDOWN_TIMEOUT_SECONDS = 5;

    /**
     * 默认资源关闭超时时间单位
     */
    public static final TimeUnit DEFAULT_SHUTDOWN_TIMEOUT_UNIT = TimeUnit.SECONDS;

    /**
     * 强制关闭后的额外等待时间（秒）
     */
    public static final long FORCE_SHUTDOWN_WAIT_SECONDS = 2;

    // ========== 公平锁等待 ==========

    /**
     * 公平锁等待超时时间（毫秒）
     * 用于防止信号丢失，定期唤醒重新检查条件
     */
    public static final long DEFAULT_FAIR_LOCK_WAIT_MS = 50;

    // ========== 背压控制 ==========

    /**
     * 背压检查间隔（毫秒）
     * 当存储满时，生产者等待空间的时间间隔
     */
    public static final long DEFAULT_BACKPRESSURE_CHECK_INTERVAL_MS = 2000;

    // ========== 重试策略 ==========

    /**
     * 默认最大重试次数
     */
    public static final int DEFAULT_MAX_RETRIES = 3;

    /**
     * 默认初始重试延迟（毫秒）
     */
    public static final long DEFAULT_INITIAL_RETRY_DELAY_MS = 1000;

    /**
     * 默认重试延迟倍数（指数退避）
     */
    public static final double DEFAULT_RETRY_MULTIPLIER = 2.0;

    // ========== 线程名称前缀 ==========

    /**
     * 存储出口执行器线程名称
     */
    public static final String THREAD_NAME_STORAGE_EGRESS = "flow-storage-egress";

    /**
     * 生产者线程名称前缀
     */
    public static final String THREAD_NAME_PREFIX_PRODUCER = "prod-";

    /**
     * 资源注册表关闭钩子线程名称
     */
    public static final String THREAD_NAME_SHUTDOWN_HOOK = "flow-resource-registry-shutdown";

    /**
     * 进度显示线程名称
     */
    public static final String THREAD_NAME_PROGRESS_DISPLAY = "flow-progress-display";
}
