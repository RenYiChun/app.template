package com.lrenyi.template.core.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局线程池持有者。
 * <p>
 * 通过 JVM shutdown hook 保证所有执行器在进程退出前有序关闭，避免资源泄漏。
 */
@Slf4j
public class ThreadConstant {
    public static final String NAME_PREFIX = "auto_create_thread_";
    public static final ExecutorService VIRTUAL_THREAD_EXECUTOR;
    public static final ScheduledExecutorService SCHEDULED_EXECUTOR_SERVICE;

    static {
        VIRTUAL_THREAD_EXECUTOR = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("tpl-vt-", 0).factory()
        );
        ScheduledThreadPoolExecutor scheduled = new ScheduledThreadPoolExecutor(
                Runtime.getRuntime().availableProcessors(),
                Thread.ofPlatform().name("tpl-sched-", 0).daemon(true).factory()
        );
        scheduled.setRemoveOnCancelPolicy(true);
        SCHEDULED_EXECUTOR_SERVICE = scheduled;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("ThreadConstant: 正在关闭全局线程池...");
            VIRTUAL_THREAD_EXECUTOR.shutdown();
            SCHEDULED_EXECUTOR_SERVICE.shutdown();
            try {
                if (!VIRTUAL_THREAD_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                    VIRTUAL_THREAD_EXECUTOR.shutdownNow();
                }
                if (!SCHEDULED_EXECUTOR_SERVICE.awaitTermination(5, TimeUnit.SECONDS)) {
                    SCHEDULED_EXECUTOR_SERVICE.shutdownNow();
                }
            } catch (InterruptedException e) {
                VIRTUAL_THREAD_EXECUTOR.shutdownNow();
                SCHEDULED_EXECUTOR_SERVICE.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("ThreadConstant: 全局线程池已关闭");
        }, "tpl-global-shutdown"));
    }

    private ThreadConstant() {}
}