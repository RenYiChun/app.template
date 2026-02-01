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
        assertEquals("", bean.getError_description());

        bean.setId("id1");
        bean.setAccess_token("access");
        bean.setRefresh_token("refresh");
        bean.setToken_type("Bearer");
        bean.setExpires_in("3600");
        bean.setError("err");
        bean.setError_description("desc");
        bean.setUserName("user");

        assertEquals("id1", bean.getId());
        assertEquals("access", bean.getAccess_token());
        assertEquals("refresh", bean.getRefresh_token());
        assertEquals("Bearer", bean.getToken_type());
        assertEquals("3600", bean.getExpires_in());
        assertEquals("err", bean.getError());
        assertEquals("desc", bean.getError_description());
        assertEquals("user", bean.getUserName());
    }
}
