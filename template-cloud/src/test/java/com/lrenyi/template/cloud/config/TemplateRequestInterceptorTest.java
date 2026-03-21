package com.lrenyi.template.cloud.config;

import java.util.List;
import com.lrenyi.template.cloud.service.OauthUtilService;
import com.lrenyi.template.core.TemplateConfigProperties;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemplateRequestInterceptorTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void applyFallsBackToClientTokenWhenHeadersConfigIsEmpty() {
        TemplateConfigProperties properties = new TemplateConfigProperties();
        properties.setEnabled(true);
        properties.getSecurity().setEnabled(true);
        properties.getFeign().setEnabled(true);
        properties.getFeign().setNotOauth(false);
        properties.getFeign().setHeaders(List.of());

        OauthUtilService oauthUtilService = mock(OauthUtilService.class);
        when(oauthUtilService.fetchToken("server")).thenReturn("client-token");
        TemplateRequestInterceptor interceptor = new TemplateRequestInterceptor(properties, oauthUtilService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertEquals(List.of("Bearer client-token"), template.headers().get("Authorization").stream().toList());
        verify(oauthUtilService).fetchToken("server");
    }

    @Test
    void applyPropagatesAuthorizationHeaderWhenConfigured() {
        TemplateConfigProperties properties = new TemplateConfigProperties();
        properties.setEnabled(true);
        properties.getSecurity().setEnabled(true);
        properties.getFeign().setEnabled(true);
        properties.getFeign().setNotOauth(false);
        properties.getFeign().setHeaders(List.of("Authorization"));

        OauthUtilService oauthUtilService = mock(OauthUtilService.class);
        TemplateRequestInterceptor interceptor = new TemplateRequestInterceptor(properties, oauthUtilService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer user-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertEquals(List.of("Bearer user-token"), template.headers().get("Authorization").stream().toList());
    }

    @Test
    void applyFallsBackToClientTokenWhenHeaderNamesIsNull() {
        TemplateConfigProperties properties = new TemplateConfigProperties();
        properties.setEnabled(true);
        properties.getSecurity().setEnabled(true);
        properties.getFeign().setEnabled(true);
        properties.getFeign().setNotOauth(false);
        properties.getFeign().setHeaders(List.of("Authorization"));

        OauthUtilService oauthUtilService = mock(OauthUtilService.class);
        when(oauthUtilService.fetchToken("server")).thenReturn("client-token");
        TemplateRequestInterceptor interceptor = new TemplateRequestInterceptor(properties, oauthUtilService);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeaderNames()).thenReturn(null);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertEquals(List.of("Bearer client-token"), template.headers().get("Authorization").stream().toList());
        verify(oauthUtilService).fetchToken("server");
    }
}
