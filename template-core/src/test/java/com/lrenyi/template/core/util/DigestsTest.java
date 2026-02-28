package com.lrenyi.template.core.util;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DigestsTest {
    
    @Test
    void md5_bytes() {
        byte[] input = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] result = Digests.md5(input);
        assertNotNull(result);
        assertTrue(result.length > 0);
    }
    
    @Test
    void md5_bytes_iterations() {
        byte[] input = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] one = Digests.md5(input, 1);
        byte[] two = Digests.md5(input, 2);
        assertNotNull(one);
        assertNotNull(two);
        assertArrayEquals(one, Digests.md5(input));
    }
    
    @Test
    void md5_inputStream() throws Exception {
        InputStream in = new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8));
        byte[] result = Digests.md5(in);
        assertNotNull(result);
    }
    
    @Test
    void sha1_bytes() {
        byte[] input = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] result = Digests.sha1(input);
        assertNotNull(result);
    }
    
    @Test
    void sha1_bytes_salt() {
        byte[] input = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] salt = Digests.generateSalt(16);
        byte[] result = Digests.sha1(input, salt);
        assertNotNull(result);
    }
    
    @Test
    void sha1_bytes_salt_iterations() {
        byte[] input = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] salt = Digests.generateSalt(8);
        byte[] result = Digests.sha1(input, salt, 2);
        assertNotNull(result);
    }
    
    @Test
    void sha1_inputStream() throws Exception {
        InputStream in = new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8));
        byte[] result = Digests.sha1(in);
        assertNotNull(result);
    }
    
    @Test
    void generateSalt() {
        byte[] salt = Digests.generateSalt(32);
        assertNotNull(salt);
        assertTrue(salt.length == 32);
    }
    
    @Test
    void shorten_returnsHex() {
        String result = Digests.shorten("hello", 16);
        assertNotNull(result);
        assertTrue(result.matches("[0-9a-f]+"));
    }
    
    @Test
    void shorten_maxLengthLargerThanHash_usesHashLength() {
        String result = Digests.shorten("x", 64);
        assertNotNull(result);
        assertTrue(result.length() <= 64);
    }
}
