package com.lrenyi.template.flow.resource;

import com.lrenyi.template.flow.internal.FlowLauncher;

/**
 * 按 jobId 查找活跃 Launcher 的接口。
 * 用于解耦 FlowResourceRegistry 与 FlowManager，避免模块间循环依赖。
 */
public interface ActiveLauncherLookup {

    /**
     * 根据 jobId 获取当前活跃的 Launcher，不存在则返回 null。
     */
    FlowLauncher<?> getActiveLauncher(String jobId);
}
