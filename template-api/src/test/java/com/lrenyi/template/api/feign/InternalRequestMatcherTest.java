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

    private static final String LOCALHOST = "127.0.0.1";

    private InternalRequestMatcher matcher;
    private HttpServletRequest request;
    
    @BeforeEach
    void setUp() {
        matcher = new InternalRequestMatcher();
        request = mock(HttpServletRequest.class);
    }
    
    @Test
    void matchesHeaderTrueWithoutWhitelistReturnsFalse() {
        when(request.getHeader(TemplateConstant.HEADER_NAME)).thenReturn("true");
        assertFalse(matcher.matches(request));
    }
    
    @Test
    void matchesHeaderNullReturnsFalse() {
        when(request.getHeader(TemplateConstant.HEADER_NAME)).thenReturn(null);
        assertFalse(matcher.matches(request));
    }
    
    @Test
    void matchesHeaderOtherReturnsFalse() {
        when(request.getHeader(TemplateConstant.HEADER_NAME)).thenReturn("false");
        assertFalse(matcher.matches(request));
        when(request.getHeader(TemplateConstant.HEADER_NAME)).thenReturn("TRUE");
        assertFalse(matcher.matches(request));
    }
    
    @Test
    void matchesWithIpPatternsMatchingIpReturnsTrue() {
        InternalRequestMatcher ipMatcher = new InternalRequestMatcher(List.of(LOCALHOST, "10.0.0.0/8"));
        when(request.getHeader(TemplateConstant.HEADER_NAME)).thenReturn("true");
        when(request.getRemoteAddr()).thenReturn(LOCALHOST);
        assertTrue(ipMatcher.matches(request));
        when(request.getRemoteAddr()).thenReturn("10.1.2.3");
        assertTrue(ipMatcher.matches(request));
    }
    
    @Test
    void matchesWithIpPatternsNonMatchingIpReturnsFalse() {
        InternalRequestMatcher ipMatcher = new InternalRequestMatcher(List.of(LOCALHOST));
        when(request.getHeader(TemplateConstant.HEADER_NAME)).thenReturn("true");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        assertFalse(ipMatcher.matches(request));
    }
}
