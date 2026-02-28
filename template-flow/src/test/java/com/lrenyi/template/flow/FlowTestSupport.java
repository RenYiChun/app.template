package com.lrenyi.template.flow;

import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.engine.FlowJoinerEngine;
import com.lrenyi.template.flow.manager.FlowManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Flow 引擎测试辅助工具。
 * <p>
 * 提供快速构建 FlowManager 和 FlowJoinerEngine 的便捷方法，
 * 避免每个测试类重复编写初始化逻辑。
 *
 * <pre>{@code
 * FlowJoinerEngine engine = FlowTestSupport.createEngine();
 * TemplateConfigProperties.Flow config = FlowTestSupport.defaultFlowConfig();
 * engine.run("test-job", myJoiner, mySource, 100, config);
 * }</pre>
 */
public final class FlowTestSupport {
    
    private FlowTestSupport() {}
    
    /**
     * 创建默认配置的 FlowJoinerEngine
     */
    public static FlowJoinerEngine createEngine() {
        return new FlowJoinerEngine(createManager());
    }
    
    /**
     * 创建默认配置的 FlowManager（使用 SimpleMeterRegistry，不发布指标）
     */
    public static FlowManager createManager() {
        return createManager(defaultFlowConfig());
    }
    
    /**
     * 创建指定配置的 FlowManager
     */
    public static FlowManager createManager(TemplateConfigProperties.Flow config) {
        FlowManager.reset();
        return FlowManager.getInstance(config, new SimpleMeterRegistry());
    }
    
    /**
     * 返回适合测试的默认 Flow 配置（小容量、短 TTL）
     */
    public static TemplateConfigProperties.Flow defaultFlowConfig() {
        TemplateConfigProperties.Flow flow = new TemplateConfigProperties.Flow();
        flow.getProducer().setMaxCacheSize(1000);
        flow.getProducer().setParallelism(4);
        flow.getConsumer().setConcurrencyLimit(100);
        flow.getConsumer().setTtlMill(5000);
        return flow;
    }
    
    /**
     * 创建指定配置的 FlowJoinerEngine
     */
    public static FlowJoinerEngine createEngine(TemplateConfigProperties.Flow config) {
        return new FlowJoinerEngine(createManager(config));
    }
    
    /**
     * 清理全局单例状态，建议在 @AfterEach 中调用
     */
    public static void cleanup() {
        FlowManager.reset();
    }
}
