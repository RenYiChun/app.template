package com.lrenyi.template.core.util;

import com.lrenyi.template.core.TemplateConfigProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class ResultTest {
    
    @Test
    void constructor_noArgs_setsDefaults() {
        Result<String> r = new Result<>();
        assertEquals(0, r.getCode());
        assertNull(r.getData());
        assertNull(r.getMessage());
    }
    
    @Test
    void constructor_messageOnly() {
        Result<String> r = new Result<>("msg");
        assertEquals("msg", r.getMessage());
    }
    
    @Test
    void getSuccess_setsCodeMessageAndData() {
        Result<String> r = Result.getSuccess("data");
        assertEquals(MCode.SUCCESS.getCode(), r.getCode());
        assertEquals(MCode.SUCCESS.getMessage(), r.getMessage());
        assertEquals("data", r.getData());
    }
    
    @Test
    void getError_setsCodeDataAndMessage() {
        Result<String> r = Result.getError("errData", "error msg");
        assertEquals(MCode.EXCEPTION.getCode(), r.getCode());
        assertEquals("errData", r.getData());
        assertEquals("error msg", r.getMessage());
    }
    
    @Test
    void makeThrowable_configNull_usesClassAndDefaultMessage() {
        try (MockedStatic<SpringContextUtil> util = mockStatic(SpringContextUtil.class)) {
            util.when(() -> SpringContextUtil.getBean(TemplateConfigProperties.class)).thenReturn(null);
            Result<Object> r = new Result<>();
            Exception cause = new RuntimeException("hidden");
            r.makeThrowable(cause, "default");
            assertEquals(MCode.EXCEPTION.getCode(), r.getCode());
            assertEquals("java.lang.RuntimeException->default", r.getMessage());
        }
    }
    
    @Test
    void makeThrowable_exportExceptionDetailFalse_usesClassAndDefaultMessage() {
        try (MockedStatic<SpringContextUtil> util = mockStatic(SpringContextUtil.class)) {
            TemplateConfigProperties config = new TemplateConfigProperties();
            config.getWeb().setExportExceptionDetail(false);
            util.when(() -> SpringContextUtil.getBean(TemplateConfigProperties.class)).thenReturn(config);
            Result<Object> r = new Result<>();
            r.makeThrowable(new RuntimeException("hidden"), "default");
            assertEquals("java.lang.RuntimeException->default", r.getMessage());
        }
    }
    
    @Test
    void makeThrowable_exportExceptionDetailTrue_hasMessage_usesCauseMessage() {
        try (MockedStatic<SpringContextUtil> util = mockStatic(SpringContextUtil.class)) {
            TemplateConfigProperties config = new TemplateConfigProperties();
            config.getWeb().setExportExceptionDetail(true);
            util.when(() -> SpringContextUtil.getBean(TemplateConfigProperties.class)).thenReturn(config);
            Result<Object> r = new Result<>();
            r.makeThrowable(new RuntimeException("visible message"), "default");
            assertEquals("visible message", r.getMessage());
        }
    }
    
    @Test
    void makeThrowable_exportExceptionDetailTrue_emptyMessage_usesClassAndDefault() {
        try (MockedStatic<SpringContextUtil> util = mockStatic(SpringContextUtil.class)) {
            TemplateConfigProperties config = new TemplateConfigProperties();
            config.getWeb().setExportExceptionDetail(true);
            util.when(() -> SpringContextUtil.getBean(TemplateConfigProperties.class)).thenReturn(config);
            Result<Object> r = new Result<>();
            r.makeThrowable(new RuntimeException(""), "default");
            assertEquals("java.lang.RuntimeException->default", r.getMessage());
        }
    }
    
    @Test
    void makeThrowable_exportExceptionDetailTrue_nullMessage_usesClassAndDefault() {
        try (MockedStatic<SpringContextUtil> util = mockStatic(SpringContextUtil.class)) {
            TemplateConfigProperties config = new TemplateConfigProperties();
            config.getWeb().setExportExceptionDetail(true);
            util.when(() -> SpringContextUtil.getBean(TemplateConfigProperties.class)).thenReturn(config);
            Result<Object> r = new Result<>();
            r.makeThrowable(new RuntimeException((String) null), "default");
            assertEquals("java.lang.RuntimeException->default", r.getMessage());
        }
    }
}
