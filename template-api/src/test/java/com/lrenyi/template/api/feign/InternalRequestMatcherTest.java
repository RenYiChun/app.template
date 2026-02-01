package com.lrenyi.template.api.feign;

import com.lrenyi.template.core.util.TemplateConstant;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalRequestMatcherTest {

    private InternalRequestMatcher matcher;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        matcher = new InternalRequestMatcher();
        request = mock(HttpServletRequest.class);
    }

    @Test
    void matches_headerTrue_returnsTrue() {
        when(request.getHeader(TemplateConstant.HEADER_NAME)).thenReturn("true");
        assertTrue(matcher.matches(request));
    }

    @Test
    void matches_headerNull_returnsFalse() {
        when(request.getHeader(TemplateConstant.HEADER_NAME)).thenReturn(null);
        assertFalse(matcher.matches(request));
    }

    @Test
    void matches_headerOther_returnsFalse() {
        when(request.getHeader(TemplateConstant.HEADER_NAME)).thenReturn("false");
        assertFalse(matcher.matches(request));
        when(request.getHeader(TemplateConstant.HEADER_NAME)).thenReturn("TRUE");
        assertFalse(matcher.matches(request));
    }
}
