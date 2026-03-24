package com.lrenyi.template.flow.api;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
     * <p><b>监控展示名</b>：在首次 {@link #startPush} 之前对返回的 {@link ProgressTracker} 调用
     * {@link ProgressTracker#setMetricJobId(String)}（如报表名），各阶段 Micrometer 的 {@code jobId}
     * 标签将使用该展示名 + 阶段后缀（如 {@code MyReport:0}），避免仪表盘上出现整段 UUID 前缀。</p>
     */
    ProgressTracker getProgressTracker();

    /**
     * 停止管道的所有阶段。
     */
    void stop(boolean force);

    /**
     * 管道构建器。
     * <p><b>语义约定（与实现对齐）</b>：下面每一次 {@code nextStage} / {@code nextMap} / {@code aggregate} / {@code sink}
     * 都会在运行时对应 <b>一个独立的 {@link com.lrenyi.template.flow.internal.FlowLauncher}</b>，即一整条 Flow 任务在引擎里的
     * 「生产侧入站 → {@link com.lrenyi.template.flow.storage.FlowStorage} 驻留（由 {@link FlowJoiner#getStorageType()} 决定实现）
     * → 出口消费（{@code onSingleConsumed} / {@code onPairConsumed} 等）→ 再交给下游」。
     * 因此 <b>不是</b> 单纯的函数式 {@code map}，而是「一段带存储与背压的完整流水线环节」。</p>
     *
     * @param <T> 当前阶段处理的数据类型
     */
    interface Builder<T> {
        /**
         * 添加一个常规处理阶段（一整段 FlowLauncher，见 {@link Builder} 接口说明）。
         *
         * @param spec 阶段配置（下游类型、Joiner、列表转换器、可选配对产出等）；后续扩展字段见 {@link NextStageSpec}
         */
        <R> Builder<R> nextStage(NextStageSpec<T, R> spec);

        /**
         * 与 {@link #nextStage(NextStageSpec)} 相同，但将攒批挂在本段消费出口（不单独增加 {@code aggregate} 段 Launcher），
         * 下游元素类型为 {@code List}{@code <R>}。
         *
         * @param batchSpec 攒批参数（条数上限与时间窗）
         */
        <R> Builder<List<R>> nextStage(NextStageSpec<T, R> spec, EmbeddedBatchSpec batchSpec);

        /**
         * 线性映射阶段：语义上仍是 <b>一整段 FlowLauncher</b>（见 {@link Builder} 接口说明），内部使用占位
         * {@link com.lrenyi.template.flow.pipeline.MapOperatorJoiner}，每条入站使用独立 {@code joinKey}，避免与
         * 引擎「按 key 驻留」语义冲突；{@link NextMapSpec#cacheProducer()} 仅在消费侧把单条转为下游类型，返回 null 时过滤该条。
         *
         * @param spec 映射段配置（驻留类型、下游类型、映射、消费节拍等）；后续扩展字段见 {@link NextMapSpec}
         */
        <R> Builder<R> nextMap(NextMapSpec<T, R> spec);

        /**
         * 与 {@link #nextMap(NextMapSpec)} 相同，但将攒批挂在本段映射的消费出口（不单独增加 {@code aggregate} 段 Launcher），
         * 下游元素类型为 {@code List}{@code <R>}。
         *
         * @param batchSpec 攒批参数（条数上限与时间窗）
         */
        <R> Builder<List<R>> nextMap(NextMapSpec<T, R> spec, EmbeddedBatchSpec batchSpec);

        /**
         * 添加一个常规处理阶段（不改变数据类型）。
         */
        default Builder<T> nextStage(FlowJoiner<T> joiner) {
            return nextStage(NextStageSpec.of(joiner.getDataType(), joiner, List::of));
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
         * 带名称的扇出：分支名称会参与子阶段 jobId 构造，便于监控面板直接识别分支来源。
         *
         * @param branches 命名子管道构建器
         * @return 无后续链条（扇出通常意味着该分支已处理或需独立汇聚）
         */
        @SuppressWarnings("unchecked")
        FlowPipeline<?> forkNamed(NamedBranchSpec<T>... branches);

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
