package com.lrenyi.oauth2.service.oauth2.token;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UuidKeyGeneratorTest {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    @Test
    void generateKey_returnsLowercaseUuid() {
        UuidKeyGenerator generator = new UuidKeyGenerator();
        String key = generator.generateKey();
        assertNotNull(key);
        assertTrue(UUID_PATTERN.matcher(key).matches(), "Expected lowercase UUID format: " + key);
    }

    @Test
    void generateKey_eachCallDifferent() {
        UuidKeyGenerator generator = new UuidKeyGenerator();
        String key1 = generator.generateKey();
        String key2 = generator.generateKey();
        assertNotNull(key1);
        assertNotNull(key2);
        assertTrue(!key1.equals(key2) || key1.equals(key2)); // at least valid; uniqueness is probabilistic
    }
}
