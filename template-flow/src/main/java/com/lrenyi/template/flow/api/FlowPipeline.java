package com.lrenyi.template.flow.api;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import com.lrenyi.template.core.TemplateConfigProperties;
import com.lrenyi.template.flow.manager.FlowManager;
import com.lrenyi.template.flow.pipeline.FlowPipelineBuilderImpl;

/**
 * 流管道：支持多阶段串联、扇出、聚合的流聚合任务。
 * 每一个阶段可以有独立的数据类型、存储类型、匹配逻辑。
 * 资源消耗（并发、存储、在途数据量）在全局层面受控。
 *
 * @param <I> 管道初始输入的数据类型
 */
public interface FlowPipeline<I> {
    
    /**
     * 以拉取模式运行管道。
     *
     * @param source     初始数据源
     * @param flowConfig 配置
     */
    void run(FlowSource<I> source, TemplateConfigProperties.Flow flowConfig);
    
    /**
     * 以推送模式启动管道。
     *
     * @param flowConfig 配置
     * @return 管道第一阶段的入口
     */
    FlowInlet<I> startPush(TemplateConfigProperties.Flow flowConfig);
    
    /**
     * 获取管道的全局进度追踪器。
     * 追踪器的 terminated 数量表示到达终（Sink）的数据量。
     */
    ProgressTracker getProgressTracker();
    
    /**
     * 停止管道的所有阶段。
     */
    void stop(boolean force);
    
    /**
     * 管道构建器。
     *
     * @param <T> 当前阶段处理的数据类型
     */
    interface Builder<T> {
        /**
         * 添加一个常规处理阶段。
         *
         * @param outputClass 该阶段转换后的输出数据类型
         * @param joiner      该阶段的聚合/存储逻辑
         * @param transformer 转换器
         * @param <R>         下一阶段的数据类型
         * @return 下一阶段的构建器
         */
        <R> Builder<R> nextStage(Class<R> outputClass, FlowJoiner<T> joiner, Function<T, List<R>> transformer);

        /**
         * 添加常规阶段，并显式声明「配对消费后」向下游的产出（一次配对一份列表，而非对两侧各走 transformer）。
         *
         * @param pairOutput 在 {@code onPairConsumed} 之后调用；返回 null 时表示与未注入时相同，回退为兼容行为或 {@link com.lrenyi.template.flow.pipeline.PipelineStageOutput}
         */
        <R> Builder<R> nextStage(Class<R> outputClass,
                                 FlowJoiner<T> joiner,
                                 Function<T, List<R>> transformer,
                                 BiFunction<T, T, List<R>> pairOutput);

        /**
         * 线性映射阶段：每条入站使用独立存储键；{@code mapper} 返回 null 时过滤该条。
         *
         * @param outputClass 映射后的类型
         * @param mapper      单条映射
         */
        <R> Builder<R> nextMap(Class<R> outputClass, Function<T, R> mapper);

        /**
         * 添加一个常规处理阶段（不改变数据类型）。
         */
        default Builder<T> nextStage(FlowJoiner<T> joiner) {
            return nextStage(joiner.getDataType(), joiner, List::of);
        }
        
        /**
         * 扇出 (Fan-out)：将当前阶段的产出广播到多个并行的子管道逻辑中。
         *
         * @param branches 子管道构建器
         * @return 无后续链条（扇出通常意味着该分支已处理或需独立汇聚）
         */
        @SuppressWarnings("unchecked")
        FlowPipeline<?> fork(Consumer<Builder<T>>... branches);
        
        /**
         * 攒批/聚合 (Batching)：将多个数据项合为一个列表下发给下一阶段。
         *
         * @param batchSize 数量阈值
         * @param timeout   时间阈值
         * @param unit      时间单位
         * @return 下一阶段（List 类型）的构建器
         */
        Builder<List<T>> aggregate(int batchSize, long timeout, TimeUnit unit);
        
        /**
         * 定义管道终点。
         *
         * @param sinkClass 最终到达数据的数据类型
         * @param onSink    最终到达数据后的业务逻辑
         * @return 可执行的管道实例
         */
        FlowPipeline<?> sink(Class<T> sinkClass, BiConsumer<T, String> onSink);

        /**
         * 定义管道终点。
         */
        default FlowPipeline<?> sink(BiConsumer<T, String> onSink) {
            return sink(null, onSink);
        }
    }
    
    /**
     * 创建管道构建器。
     *
     * @param jobId      管道的唯一标识名
     * @param inputClass 初始输入的数据类型
     * @param <I>        初始输入类型
     */
    static <I> Builder<I> builder(String jobId, Class<I> inputClass, FlowManager flowManager) {
        return new FlowPipelineBuilderImpl<>(jobId, inputClass, flowManager);
    }
}
