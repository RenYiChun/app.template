package com.lrenyi.template.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MCodeTest {
    
    @Test
    void values_haveCorrectCodeAndMessage() {
        assertEquals(200, MCode.SUCCESS.getCode());
        assertEquals("success", MCode.SUCCESS.getMessage());
        
        assertEquals(201, MCode.SHOW_EXCEPTION_MESSAGE.getCode());
        assertEquals("非法访问", MCode.SHOW_EXCEPTION_MESSAGE.getMessage());
        
        assertEquals(202, MCode.PASSWORD_ERROR.getCode());
        assertEquals("密码错误", MCode.PASSWORD_ERROR.getMessage());
        
        assertEquals(500, MCode.EXCEPTION.getCode());
        assertEquals("内部服务异常", MCode.EXCEPTION.getMessage());
        
        assertEquals(401, MCode.NO_PERMISSIONS.getCode());
        assertEquals("未认证", MCode.NO_PERMISSIONS.getMessage());
    }
    
    @Test
    void valueOf_returnsCorrectEnum() {
        assertEquals(MCode.SUCCESS, MCode.valueOf("SUCCESS"));
        assertEquals(MCode.EXCEPTION, MCode.valueOf("EXCEPTION"));
    }
}
