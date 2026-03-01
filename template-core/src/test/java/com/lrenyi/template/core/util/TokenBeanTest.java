package com.lrenyi.template.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TokenBeanTest {
    
    @Test
    void gettersAndSetters_workCorrectly() {
        TokenBean bean = new TokenBean();
        assertNull(bean.getId());
        assertEquals("", bean.getError());
        assertEquals("", bean.getErrorDescription());
        
        bean.setId("id1");
        bean.setAccessToken("access");
        bean.setRefreshToken("refresh");
        bean.setTokenType("Bearer");
        bean.setExpiresIn("3600");
        bean.setError("err");
        bean.setErrorDescription("desc");
        bean.setUserName("user");
        
        assertEquals("id1", bean.getId());
        assertEquals("access", bean.getAccessToken());
        assertEquals("refresh", bean.getRefreshToken());
        assertEquals("Bearer", bean.getTokenType());
        assertEquals("3600", bean.getExpiresIn());
        assertEquals("err", bean.getError());
        assertEquals("desc", bean.getErrorDescription());
        assertEquals("user", bean.getUserName());
    }
}
