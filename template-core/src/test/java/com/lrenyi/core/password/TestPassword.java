package com.lrenyi.core.password;

import com.lrenyi.template.core.coder.DefaultTemplateEncryptService;
import java.nio.charset.StandardCharsets;
import org.apache.commons.codec.digest.Md5Crypt;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestPassword {
    static DefaultTemplateEncryptService encoder = new DefaultTemplateEncryptService();
    
    @Test
    public void testEncoder() {
        encoder.setDefaultPasswordEncoderForMatches("default");
        String rawPassword = "app.template";
        String encode = encoder.encode(rawPassword);
        boolean matches = encoder.matches(rawPassword, encode);
        Assertions.assertTrue(matches);
    }
    
    @Test
    public void defaultCoder() {
        encoder.setDefaultPasswordEncoderForMatches("default");
        String rawPassword = "123456";
        String encode = encoder.encode(rawPassword);
        boolean matches = encoder.matches(rawPassword, encode);
        Assertions.assertTrue(matches);
    }
    
    @Test
    public void testRsaEncoder() {
        encoder.setDefaultPasswordEncoderForMatches("RSA2048");
        String rawP = "app.template";
        String encode = encoder.encode(rawP);
        boolean matches = encoder.matches(rawP, encode);
        Assertions.assertTrue(matches);
    }
    
    @Test
    public void testNoopEncoder() {
        encoder.setDefaultPasswordEncoderForMatches("noop");
        String rawP = "app.template";
        String encode = encoder.encode(rawP);
        boolean matches = encoder.matches("app.template", encode);
        Assertions.assertTrue(matches);
    }
    
    @Test
    public void testSHA() {
        encoder.setDefaultPasswordEncoderForMatches("SHA-1");
        String encoded = encoder.encode("app.template");
        String rawP = "app.template";
        boolean matches = encoder.matches(rawP, encoded);
        Assertions.assertTrue(matches);
        
        String data = "eyJraWQiOiJudWxsIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiJkZWZhdWx0LWNsaWVudC1pZCIsImF1ZCI6ImRlZmF1bHQtY2xpZW50LWlkIiwibmJmIjoxNzEwODA5NzI4LCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODEiLCJleHAiOjE3MTA4MTMzMjgsImlhdCI6MTcxMDgwOTcyOCwianRpIjoiZTRjNDQ4MWUtNTBiNS00MTEwLTg5YTMtMGIxNDRjYTQzNmFkIn0.aO3xYWl-Do-0-SlZdcy4eAdqQ45lpJRuJ_c6zs1WUlzWQirrU3kT4LSwUefPEvPczMcof2nhWaJ0o3V7uvQimTZn0co9Yjmi2TlBCHdB-4vs5D6WUKy45XjwsQKePVmtmRpU7tUn1PKuAoydugC1vVgkw3loaDhOTp104B5_8eShXfb_J7yPV0kndpNDBhZNov-YvMVXM3yrBt8FP0jI682TpUwKS6CLWfBAaHn72US_wQ30eVlLa0KNeeiZPHpJkZTJncBIdr_seISjcms490KDXJdjvE-Q5XxN15tsDfd3BZTw3rYjgA6NaBQCEjPBXq3Yu2XmnM10c8fL79nupg";
        String id1 = Md5Crypt.md5Crypt(data.getBytes(StandardCharsets.UTF_8), "riabcddegc", "ri");
        String id2 = Md5Crypt.md5Crypt(data.getBytes(StandardCharsets.UTF_8),"riabcddegc", "ri");
        Assertions.assertEquals(id1, id2);
    }
}
