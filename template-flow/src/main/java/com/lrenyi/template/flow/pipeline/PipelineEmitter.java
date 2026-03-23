package com.lrenyi.template.flow.pipeline;

import java.util.function.Consumer;

/**
 * 管道发射器接口。
 * 如果某个 Joiner 需要在 `onPairConsumed` 等回调中主动向下游发送数据（而非依赖 PipelineJoinerWrapper 的自动转换），
 * 则需要实现此接口。
 *
 * @param <T> 下发给下游的数据类型
 */
public interface PipelineEmitter<T> {
    void setDownstream(Consumer<T> downstream);
}
