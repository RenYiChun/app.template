package com.lrenyi.template.cloud.config;

import java.nio.charset.StandardCharsets;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

@Slf4j
public class FeignClientErrorDecoder implements ErrorDecoder {

    @Override
    public Exception decode(String methodKey, Response response) {
        String body = "";
        try {
            body = IOUtils.toString(response.body().asInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.debug("Could not read response body for {}: {}", methodKey, e.getMessage());
        }
        String message = String.format("feign[%s] invocation fail, status= %s, body= %s",
                                       methodKey,
                                       response.status(),
                                       body
        );
        return new RuntimeException(message);
    }
}
