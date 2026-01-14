package com.lrenyi.template.api.feign;

import com.lrenyi.template.core.util.TemplateConstant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

public class InternalRequestMatcher implements RequestMatcher {
    
    @Override
    public boolean matches(HttpServletRequest request) {
        String internalCall = request.getHeader(TemplateConstant.HEADER_NAME);
        return "true".equals(internalCall);
    }
}
