package com.lrenyi.template.web.config;

import com.lrenyi.template.core.config.json.JsonService;
import com.lrenyi.template.core.util.Result;
import com.nimbusds.common.contenttype.ContentType;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
public class WebGlobalConfig implements WebMvcConfigurer {
    
    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
    
    @ControllerAdvice
    public static class GlobalExceptionHandler {
        
        @ExceptionHandler(Throwable.class)
        public void handleException(HttpServletRequest request,
                                    JsonService jsonService,
                                    HttpServletResponse response,
                                    Throwable cause) {
            log.error("发生了未被处理的异常.", cause);
            Result<String> error = new Result<>();
            error.makeThrowable(cause, "发生了未被处理的异常");
            error.setData(request.getRequestURI());
            String jsonString = jsonService.serialize(error);
            response.setContentType(ContentType.APPLICATION_JSON.getType());
            try {
                ServletOutputStream outputStream = response.getOutputStream();
                outputStream.write(jsonString.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            } catch (IOException e) {
                log.error("返回异常结果给前端时出现异常", e);
            }
        }
    }
}
