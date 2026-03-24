package com.lrenyi.template.flow.pipeline;

import com.lrenyi.template.core.TemplateConfigProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlowPipelineConfigOverlayTest {

    @Test
    void copyWithPerJobStorageCapacity_producesIndependentPerJob() {
        TemplateConfigProperties.Flow base = new TemplateConfigProperties.Flow();
        base.getLimits().getPerJob().setStorageCapacity(999);
        base.getLimits().getPerJob().getKeyedCache().setCacheTtlMill(12_345L);

        TemplateConfigProperties.Flow copy = FlowPipelineConfigOverlay.copyWithPerJobStorageCapacity(base, 50);
        assertEquals(50, copy.getLimits().getPerJob().getStorageCapacity());
        assertEquals(12_345L, copy.getLimits().getPerJob().getKeyedCache().getCacheTtlMill());

        base.getLimits().getPerJob().setStorageCapacity(1);
        assertEquals(50, copy.getLimits().getPerJob().getStorageCapacity());
    }
}
