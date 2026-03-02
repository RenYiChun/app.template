package com.lrenyi.template.cloud.config;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeignClientErrorDecoderTest {
    
    private FeignClientErrorDecoder decoder;
    
    @BeforeEach
    void setUp() {
        decoder = new FeignClientErrorDecoder();
    }
    
    @Test
    void decode_bodyReadSuccess_includesBodyInMessage() {
        String bodyContent = "error detail";
        Response.Body body = new Response.Body() {
            @Override
            public Integer length() {
                return bodyContent.getBytes(StandardCharsets.UTF_8).length;
            }
            
            @Override
            public boolean isRepeatable() {
                return true;
            }
            
            @Override
            public InputStream asInputStream() {
                return new ByteArrayInputStream(bodyContent.getBytes(StandardCharsets.UTF_8));
            }
            
            @Override
            public Reader asReader(Charset charset) {
                return new StringReader(bodyContent);
            }
            
            @Override
            public void close() {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        };
        Request request = Request.create(Request.HttpMethod.GET,
                                         "/api",
                                         Collections.emptyMap(),
                                         (byte[]) null,
                                         StandardCharsets.UTF_8,
                                         null
        );
        Response response = Response.builder()
                                    .status(500)
                                    .reason("Internal Server Error")
                                    .headers(Collections.emptyMap())
                                    .body(body)
                                    .request(request)
                                    .build();
        
        Exception result = decoder.decode("methodKey", response);
        
        assertNotNull(result);
        assertTrue(result instanceof RuntimeException);
        assertTrue(result.getMessage().contains("methodKey"));
        assertTrue(result.getMessage().contains("500"));
        assertTrue(result.getMessage().contains("error detail"));
    }
    
    @Test
    void decode_bodyReadFails_messageStillFormatted() {
        Response.Body body = new Response.Body() {
            @Override
            public Integer length() {
                return null;
            }
            
            @Override
            public boolean isRepeatable() {
                return false;
            }
            
            @Override
            public InputStream asInputStream() {
                throw new RuntimeException("stream fail");
            }
            
            @Override
            public Reader asReader(Charset charset) {
                throw new RuntimeException("reader fail");
            }
            
            @Override
            public void close() {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        };
        Request request = Request.create(Request.HttpMethod.GET,
                                         "/api",
                                         Collections.emptyMap(),
                                         (byte[]) null,
                                         StandardCharsets.UTF_8,
                                         null
        );
        Response response = Response.builder()
                                    .status(404)
                                    .reason("Not Found")
                                    .headers(Collections.emptyMap())
                                    .body(body)
                                    .request(request)
                                    .build();
        
        Exception result = decoder.decode("getUser", response);
        
        assertNotNull(result);
        assertTrue(result.getMessage().contains("getUser"));
        assertTrue(result.getMessage().contains("404"));
        assertTrue(result.getMessage().contains("body="));
    }
}
