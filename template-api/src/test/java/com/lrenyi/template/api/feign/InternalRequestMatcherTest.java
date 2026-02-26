package com.lrenyi.template.api.feign;

import java.util.List;
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

    @Test
    void matches_withIpPatterns_matchingIp_returnsTrue() {
        InternalRequestMatcher ipMatcher = new InternalRequestMatcher(List.of("127.0.0.1", "10.0.0.0/8"));
        when(request.getHeader(TemplateConstant.HEADER_NAME)).thenReturn("true");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        assertTrue(ipMatcher.matches(request));
        when(request.getRemoteAddr()).thenReturn("10.1.2.3");
        assertTrue(ipMatcher.matches(request));
    }

    @Test
    void matches_withIpPatterns_nonMatchingIp_returnsFalse() {
        InternalRequestMatcher ipMatcher = new InternalRequestMatcher(List.of("127.0.0.1"));
        when(request.getHeader(TemplateConstant.HEADER_NAME)).thenReturn("true");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        assertFalse(ipMatcher.matches(request));
    }
}
