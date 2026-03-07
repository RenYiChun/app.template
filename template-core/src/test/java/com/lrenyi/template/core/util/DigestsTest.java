package com.lrenyi.template.core.util;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DigestsTest {
    
    private static final String HELLO = "hello";

    @Test
    void md5Bytes() {
        byte[] input = HELLO.getBytes(StandardCharsets.UTF_8);
        byte[] result = Digests.md5(input);
        assertNotNull(result);
        assertTrue(result.length > 0);
    }
    
    @Test
    void md5BytesIterations() {
        byte[] input = HELLO.getBytes(StandardCharsets.UTF_8);
        byte[] one = Digests.md5(input, 1);
        byte[] two = Digests.md5(input, 2);
        assertNotNull(one);
        assertNotNull(two);
        assertArrayEquals(one, Digests.md5(input));
    }
    
    @Test
    void md5InputStream() throws Exception {
        InputStream in = new ByteArrayInputStream(HELLO.getBytes(StandardCharsets.UTF_8));
        byte[] result = Digests.md5(in);
        assertNotNull(result);
    }
    
    @Test
    void sha1Bytes() {
        byte[] input = HELLO.getBytes(StandardCharsets.UTF_8);
        byte[] result = Digests.sha1(input);
        assertNotNull(result);
    }
    
    @Test
    void sha1BytesSalt() {
        byte[] input = HELLO.getBytes(StandardCharsets.UTF_8);
        byte[] salt = Digests.generateSalt(16);
        byte[] result = Digests.sha1(input, salt);
        assertNotNull(result);
    }
    
    @Test
    void sha1BytesSaltIterations() {
        byte[] input = HELLO.getBytes(StandardCharsets.UTF_8);
        byte[] salt = Digests.generateSalt(8);
        byte[] result = Digests.sha1(input, salt, 2);
        assertNotNull(result);
    }
    
    @Test
    void sha1InputStream() throws Exception {
        InputStream in = new ByteArrayInputStream(HELLO.getBytes(StandardCharsets.UTF_8));
        byte[] result = Digests.sha1(in);
        assertNotNull(result);
    }
    
    @Test
    void generateSalt() {
        byte[] salt = Digests.generateSalt(32);
        assertNotNull(salt);
        assertEquals(32, salt.length);
    }
    
    @Test
    void shortenReturnsHex() {
        String result = Digests.shorten(HELLO, 16);
        assertNotNull(result);
        assertTrue(result.matches("[0-9a-f]+"));
    }
    
    @Test
    void shortenMaxLengthLargerThanHashUsesHashLength() {
        String result = Digests.shorten("x", 64);
        assertNotNull(result);
        assertTrue(result.length() <= 64);
    }
}
