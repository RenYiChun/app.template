package com.lrenyi.oauth2.service.oauth2;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.lrenyi.template.core.util.StringUtils;
import com.lrenyi.template.core.util.TokenBean;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TemplateAuthenticationFailureHandler implements AuthenticationFailureHandler {
    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        if (exception instanceof OAuth2AuthenticationException authenticationException) {
            OAuth2Error error = authenticationException.getError();
            String errorCode = error.getErrorCode();
            String description = error.getDescription();
            TokenBean result = new TokenBean();
            result.setError(errorCode);
            if (StringUtils.hasLength(description)) {
                result.setError_description(description);
            }
            response.setContentType(MediaType.APPLICATION_JSON.toString());
            ServletOutputStream outputStream = response.getOutputStream();
            outputStream.write(JSON.toJSONBytes(result, JSONWriter.Feature.WriteNullStringAsEmpty));
            outputStream.flush();
            return;
        }
        String simpleName = AuthenticationException.class.getSimpleName();
        String name = OAuth2AuthenticationException.class.getName();
        log.warn(simpleName + " must be of type " + name + " but was " + exception.getClass().getName());
    }
}
