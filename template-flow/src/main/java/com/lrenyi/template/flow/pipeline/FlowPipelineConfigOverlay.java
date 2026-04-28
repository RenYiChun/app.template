package com.lrenyi.template.flow.pipeline;

import com.lrenyi.template.core.TemplateConfigProperties;

/**
 * 为管道单阶段生成独立的 {@link TemplateConfigProperties.Flow} 快照，避免与基底配置共享可变子对象。
 * 当前支持覆盖 {@code limits.per-job.storage-capacity} 与 {@code limits.per-job.consumer-threads}。
 */
public final class FlowPipelineConfigOverlay {

    private FlowPipelineConfigOverlay() {
    }

    /**
     * 深拷贝基底 Flow，并将 {@code limits.per-job.storage-capacity} 设为指定值；其余与基底一致。
     *
     * @param base            运行时传入的基底配置（不得为 null）
     * @param storageCapacity 本阶段存储条数上限，必须 {@code > 0}
     */
    public static TemplateConfigProperties.Flow copyWithPerJobStorageCapacity(
            TemplateConfigProperties.Flow base,
            int storageCapacity) {
        return copyWithPerJobOverrides(base, storageCapacity, null);
    }

    /**
     * 深拷贝基底 Flow，并按需覆盖 per-job 级别的单阶段限制；其余与基底一致。
     */
    public static TemplateConfigProperties.Flow copyWithPerJobOverrides(
            TemplateConfigProperties.Flow base,
            Integer storageCapacity,
            Integer consumerThreads) {
        if (storageCapacity != null && storageCapacity <= 0) {
            throw new IllegalArgumentException("per-stage storageCapacity must be > 0, got " + storageCapacity);
        }
        if (consumerThreads != null && consumerThreads <= 0) {
            throw new IllegalArgumentException("per-stage consumerThreads must be > 0, got " + consumerThreads);
        }
        if (storageCapacity == null && consumerThreads == null) {
            return base;
        }
        TemplateConfigProperties.Flow out = new TemplateConfigProperties.Flow();
        out.setProducerBackpressureBlockingMode(base.getProducerBackpressureBlockingMode());
        out.setProducerBackpressureTimeoutMill(base.getProducerBackpressureTimeoutMill());
        out.setConsumerAcquireBlockingMode(base.getConsumerAcquireBlockingMode());
        out.setConsumerAcquireTimeoutMill(base.getConsumerAcquireTimeoutMill());
        out.setShowStatus(base.isShowStatus());

        TemplateConfigProperties.Flow.Limits srcLimits = base.getLimits();
        TemplateConfigProperties.Flow.Limits dstLimits = new TemplateConfigProperties.Flow.Limits();
        dstLimits.setGlobal(copyGlobal(srcLimits.getGlobal()));
        dstLimits.setPerJob(copyPerJobWithOverrides(srcLimits.getPerJob(), storageCapacity, consumerThreads));
        out.setLimits(dstLimits);
        return out;
    }

    private static TemplateConfigProperties.Flow.Global copyGlobal(TemplateConfigProperties.Flow.Global g) {
        TemplateConfigProperties.Flow.Global ng = new TemplateConfigProperties.Flow.Global();
        ng.setFairScheduling(g.isFairScheduling());
        ng.setProducerThreads(g.getProducerThreads());
        ng.setStorageCapacity(g.getStorageCapacity());
        ng.setConsumerThreads(g.getConsumerThreads());
        ng.setSinkConsumerThreads(g.getSinkConsumerThreads());
        ng.setEvictionCoordinatorThreads(g.getEvictionCoordinatorThreads());
        ng.setEvictionScanIntervalMill(g.getEvictionScanIntervalMill());
        return ng;
    }

    private static TemplateConfigProperties.Flow.PerJob copyPerJobWithOverrides(
            TemplateConfigProperties.Flow.PerJob p,
            Integer storageCapacity,
            Integer consumerThreads) {
        TemplateConfigProperties.Flow.PerJob np = new TemplateConfigProperties.Flow.PerJob();
        np.setProducerThreads(p.getProducerThreads());
        np.setConsumerThreads(consumerThreads != null ? consumerThreads : p.getConsumerThreads());
        np.setStorageCapacity(storageCapacity != null ? storageCapacity : p.getStorageCapacity());
        np.setQueuePollIntervalMill(p.getQueuePollIntervalMill());
        np.setEvictionCoordinatorThreads(p.getEvictionCoordinatorThreads());
        np.setEvictionScanIntervalMill(p.getEvictionScanIntervalMill());
        np.setKeyedCache(copyKeyedCache(p.getKeyedCache()));
        return np;
    }

    private static TemplateConfigProperties.Flow.KeyedCache copyKeyedCache(TemplateConfigProperties.Flow.KeyedCache k) {
        TemplateConfigProperties.Flow.KeyedCache nk = new TemplateConfigProperties.Flow.KeyedCache();
        nk.setMultiValueEnabled(k.isMultiValueEnabled());
        nk.setMultiValueMaxPerKey(k.getMultiValueMaxPerKey());
        nk.setMultiValueOverflowPolicy(k.getMultiValueOverflowPolicy());
        nk.setCacheTtlMill(k.getCacheTtlMill());
        nk.setMustMatchRetryEnabled(k.isMustMatchRetryEnabled());
        nk.setMustMatchRetryMaxTimes(k.getMustMatchRetryMaxTimes());
        nk.setMustMatchRetryBackoffMill(k.getMustMatchRetryBackoffMill());
        nk.setPairingMultiMatchEnabled(k.getPairingMultiMatchEnabled());
        return nk;
    }
}
