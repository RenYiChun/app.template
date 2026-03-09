package com.lrenyi.template.flow.internal;

/**
 * 背压快照提供者：从底层资源采样出当前多维背压视图。
 */
public interface BackpressureSnapshotProvider {

    BackpressureSnapshot snapshot();

}

