package com.lrenyi.template.core.flow.storage;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.core.flow.FlowJoiner;
import com.lrenyi.template.core.flow.ProgressTracker;
import com.lrenyi.template.core.flow.config.FlowStorageType;
import com.lrenyi.template.core.flow.impl.FlowFinalizer;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Flow 存储工厂接口
 * 用于创建不同类型的 FlowStorage 实例
 * <p>
 * 实现类可以通过 Java SPI 机制注册，实现插件式扩展
 */
public interface FlowStorageFactory {
    
    /**
     * 获取支持的存储类型
     *
     * @return 存储类型
     */
    FlowStorageType getSupportedType();
    
    /**
     * 检查是否支持指定的存储类型
     *
     * @param type 存储类型
     *
     * @return true 如果支持，false 否则
     */
    boolean supports(FlowStorageType type);
    
    /**
     * 创建存储实例
     *
     * @param jobId                 Job ID
     * @param joiner                FlowJoiner 实例
     * @param config                Job 配置
     * @param finalizer             FlowFinalizer 实例
     * @param progressTracker       ProgressTracker 实例
     * @param storageEgressExecutor 存储出口执行器
     * @param <T>                   数据类型
     *
     * @return FlowStorage 实例
     */
    <T> FlowStorage<T> createStorage(String jobId,
                                     FlowJoiner<T> joiner,
                                     TemplateConfigProperties.JobConfig config,
                                     FlowFinalizer<T> finalizer,
                                     ProgressTracker progressTracker,
                                     ScheduledExecutorService storageEgressExecutor);
    
    /**
     * 获取工厂优先级
     * 数字越小优先级越高，当多个工厂支持同一类型时，选择优先级最高的
     *
     * @return 优先级
     */
    default int getPriority() {
        return 100;
    }
}
