package com.lrenyi.template.cloud.config;

import feign.Response;
import feign.codec.ErrorDecoder;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;

public class FeignClientErrorDecoder implements ErrorDecoder {
    @Override
    public Exception decode(String methodKey, Response response) {
        String body = "";
        try {
            body = IOUtils.toString(response.body().asInputStream(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
        return new RuntimeException("Feign 调用失败, status=" + response.status() + ", body=" + body);
    }
}
