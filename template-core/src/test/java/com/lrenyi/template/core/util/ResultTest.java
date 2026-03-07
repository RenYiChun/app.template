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

    private static final String DEFAULT_MESSAGE = "default";
    private static final String RUNTIME_EXCEPTION_DEFAULT_MESSAGE = "java.lang.RuntimeException->default";

    @Test
    void constructorNoArgsSetsDefaults() {
        Result<String> r = new Result<>();
        assertEquals(0, r.getCode());
        assertNull(r.getData());
        assertNull(r.getMessage());
    }
    
    @Test
    void constructorMessageOnly() {
        Result<String> r = new Result<>("msg");
        assertEquals("msg", r.getMessage());
    }
    
    @Test
    void getSuccessSetsCodeMessageAndData() {
        Result<String> r = Result.getSuccess("data");
        assertEquals(MCode.SUCCESS.getCode(), r.getCode());
        assertEquals(MCode.SUCCESS.getMessage(), r.getMessage());
        assertEquals("data", r.getData());
    }
    
    @Test
    void getErrorSetsCodeDataAndMessage() {
        Result<String> r = Result.getError("errData", "error msg");
        assertEquals(MCode.EXCEPTION.getCode(), r.getCode());
        assertEquals("errData", r.getData());
        assertEquals("error msg", r.getMessage());
    }
    
    @Test
    void makeThrowableConfigNullUsesClassAndDefaultMessage() {
        try (MockedStatic<SpringContextUtil> util = mockStatic(SpringContextUtil.class)) {
            util.when(() -> SpringContextUtil.getBean(TemplateConfigProperties.class)).thenReturn(null);
            Result<Object> r = new Result<>();
            Exception cause = new RuntimeException("hidden");
            r.makeThrowable(cause, DEFAULT_MESSAGE);
            assertEquals(MCode.EXCEPTION.getCode(), r.getCode());
            assertEquals(RUNTIME_EXCEPTION_DEFAULT_MESSAGE, r.getMessage());
        }
    }
    
    @Test
    void makeThrowableExportExceptionDetailFalseUsesClassAndDefaultMessage() {
        try (MockedStatic<SpringContextUtil> util = mockStatic(SpringContextUtil.class)) {
            TemplateConfigProperties config = new TemplateConfigProperties();
            config.getWeb().setExportExceptionDetail(false);
            util.when(() -> SpringContextUtil.getBean(TemplateConfigProperties.class)).thenReturn(config);
            Result<Object> r = new Result<>();
            r.makeThrowable(new RuntimeException("hidden"), DEFAULT_MESSAGE);
            assertEquals(RUNTIME_EXCEPTION_DEFAULT_MESSAGE, r.getMessage());
        }
    }
    
    @Test
    void makeThrowableExportExceptionDetailTrueHasMessageUsesCauseMessage() {
        try (MockedStatic<SpringContextUtil> util = mockStatic(SpringContextUtil.class)) {
            TemplateConfigProperties config = new TemplateConfigProperties();
            config.getWeb().setExportExceptionDetail(true);
            util.when(() -> SpringContextUtil.getBean(TemplateConfigProperties.class)).thenReturn(config);
            Result<Object> r = new Result<>();
            r.makeThrowable(new RuntimeException("visible message"), DEFAULT_MESSAGE);
            assertEquals("visible message", r.getMessage());
        }
    }
    
    @Test
    void makeThrowableExportExceptionDetailTrueEmptyMessageUsesClassAndDefault() {
        try (MockedStatic<SpringContextUtil> util = mockStatic(SpringContextUtil.class)) {
            TemplateConfigProperties config = new TemplateConfigProperties();
            config.getWeb().setExportExceptionDetail(true);
            util.when(() -> SpringContextUtil.getBean(TemplateConfigProperties.class)).thenReturn(config);
            Result<Object> r = new Result<>();
            r.makeThrowable(new RuntimeException(""), DEFAULT_MESSAGE);
            assertEquals(RUNTIME_EXCEPTION_DEFAULT_MESSAGE, r.getMessage());
        }
    }
    
    @Test
    void makeThrowableExportExceptionDetailTrueNullMessageUsesClassAndDefault() {
        try (MockedStatic<SpringContextUtil> util = mockStatic(SpringContextUtil.class)) {
            TemplateConfigProperties config = new TemplateConfigProperties();
            config.getWeb().setExportExceptionDetail(true);
            util.when(() -> SpringContextUtil.getBean(TemplateConfigProperties.class)).thenReturn(config);
            Result<Object> r = new Result<>();
            r.makeThrowable(new RuntimeException((String) null), DEFAULT_MESSAGE);
            assertEquals(RUNTIME_EXCEPTION_DEFAULT_MESSAGE, r.getMessage());
        }
    }
}
