package com.lrenyi.template.core.flow.display;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FlowProgressTableLayout 单元测试
 */
class FlowProgressTableLayoutTest {

    @Test
    void renderLine_returnsNonEmpty() {
        String line = FlowProgressTableLayout.renderLine();
        assertNotNull(line);
        assertTrue(line.length() > 0);
    }

    @Test
    void fix_padsToWidth() {
        assertEquals("a   ", FlowProgressTableLayout.fix("a", 4));
        assertEquals("ab  ", FlowProgressTableLayout.fix("ab", 4));
    }

    @Test
    void fix_nullTreatedAsEmpty() {
        assertEquals("    ", FlowProgressTableLayout.fix(null, 4));
    }

    @Test
    void formatDuration_formatsCorrectly() {
        assertEquals("00:00:00", FlowProgressTableLayout.formatDuration(0));
        assertEquals("00:01:30", FlowProgressTableLayout.formatDuration(90));
        assertEquals("01:00:00", FlowProgressTableLayout.formatDuration(3600));
    }
}
