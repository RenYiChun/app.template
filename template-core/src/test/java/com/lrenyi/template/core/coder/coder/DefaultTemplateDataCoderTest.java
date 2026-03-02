package com.lrenyi.template.core.coder.coder;

import java.nio.charset.StandardCharsets;
import com.lrenyi.template.core.util.Digests;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DefaultTemplateDataCoder 单元测试
 */
class DefaultTemplateDataCoderTest {
    
    private final DefaultTemplateDataCoder coder = new DefaultTemplateDataCoder();
    
    @Test
    void encode_matches_v2Format() {
        String raw = "test-password-123";
        String encoded = coder.encode(raw);
        assertTrue(encoded.startsWith("v2:"));
        assertTrue(coder.matches(raw, encoded));
        assertFalse(coder.matches("wrong", encoded));
    }
    
    @Test
    void matches_legacySha1Format() {
        String raw = "legacy-password";
        byte[] salt = Digests.generateSalt(8);
        byte[] hash = Digests.sha1(raw.getBytes(StandardCharsets.UTF_8), salt, 1024);
        String legacy = new String(Hex.encodeHex(salt)) + new String(Hex.encodeHex(hash));
        assertTrue(coder.matches(raw, legacy));
        assertFalse(coder.matches("wrong", legacy));
    }
}
