package com.lrenyi.template.core.flow.context;

import java.util.concurrent.atomic.AtomicInteger;
import com.lrenyi.template.core.TemplateConfigProperties;
import lombok.Getter;

/**
 * 任务注册信息 - 记录单个 Job 在全局资源池中的实时占用情况
 */
@Getter
public class Registration {
    private final String jobId;
    // 当前 Job 已经获取且尚未归还的信号量许可数
    private final AtomicInteger activeCount = new AtomicInteger(0);
    private final TemplateConfigProperties.JobConfig jobConfig;
    
    public Registration(String jobId, TemplateConfigProperties.JobConfig jobConfig) {
        this.jobId = jobId;
        this.jobConfig = jobConfig;
    }
    
    public void increment() {
        activeCount.incrementAndGet();
    }
    
    public void decrement() {
        activeCount.decrementAndGet();
    }
}