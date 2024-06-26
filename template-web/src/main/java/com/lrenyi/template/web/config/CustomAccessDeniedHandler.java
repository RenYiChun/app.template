package com.lrenyi.template.web.config;

import com.lrenyi.template.web.config.WebGlobalConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;


public class CustomAccessDeniedHandler implements AccessDeniedHandler {
    private final WebGlobalConfig.GlobalExceptionHandler globalExceptionHandler =
            new WebGlobalConfig.GlobalExceptionHandler();
    
    @Override
    public void handle(HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        globalExceptionHandler.handleException(request, response, accessDeniedException);
    }
}
