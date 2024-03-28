package com.lrenyi.template.core.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class ThreadConstant {
    public static final String NAME_PREFIX = "auto_create_thread_";
    public static final ExecutorService VIRTUAL_THREAD_EXECUTOR;
    
    public static final ScheduledExecutorService SCHEDULED_EXECUTOR_SERVICE;
    
    static {
        VIRTUAL_THREAD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
        SCHEDULED_EXECUTOR_SERVICE = new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors());
    }
}
