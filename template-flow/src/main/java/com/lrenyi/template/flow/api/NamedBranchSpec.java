package com.lrenyi.template.flow.api;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 为 {@link FlowPipeline.Builder#forkNamed(NamedBranchSpec[])} 提供分支名称与构建逻辑。
 *
 * @param <T> 分支输入类型
 * @param name 分支名称，将参与子阶段 jobId 构造
 * @param branch 分支构建逻辑
 */
public record NamedBranchSpec<T>(String name, Consumer<FlowPipeline.Builder<T>> branch) {

    public NamedBranchSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(branch, "branch");
    }

    public static <T> NamedBranchSpec<T> of(String name, Consumer<FlowPipeline.Builder<T>> branch) {
        return new NamedBranchSpec<>(name, branch);
    }
}
