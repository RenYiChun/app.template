package com.lrenyi.oauth2.service.oauth2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import com.lrenyi.template.core.json.JsonService;
import com.lrenyi.template.core.util.TokenBean;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@AllArgsConstructor
public class TemplateAuthenticationFailureHandler implements AuthenticationFailureHandler {
    private final JsonService jsonService;
    private final MeterRegistry meterRegistry;
    
    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        if (exception instanceof OAuth2AuthenticationException authenticationException) {
            OAuth2Error error = authenticationException.getError();
            String errorCode = error.getErrorCode();
            Counter.builder("app.template.oauth2.token.failed")
                   .tag("grantType", "unknown")
                   .tag("errorType", errorCode)
                   .register(meterRegistry)
                   .increment();
            String description = error.getDescription();
            TokenBean result = new TokenBean();
            result.setError(errorCode);
            if (StringUtils.hasLength(description)) {
                result.setErrorDescription(description);
            }
            response.setContentType(MediaType.APPLICATION_JSON.toString());
            ServletOutputStream outputStream = response.getOutputStream();
            outputStream.write(jsonService.serialize(result).getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            return;
        }
        String simpleName = AuthenticationException.class.getSimpleName();
        String name = OAuth2AuthenticationException.class.getName();
        log.warn("{} must be of type {} but was {}", simpleName, name, exception.getClass().getName());
    }
}
