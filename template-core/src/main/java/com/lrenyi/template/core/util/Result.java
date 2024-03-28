package com.lrenyi.template.core.util;

import com.lrenyi.template.core.config.properties.ExceptionConfig;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Result<T> {
    private int code;
    private T data;
    private String message;
    
    public Result(String message) {
        this.message = message;
    }
    
    public static <T> Result<T> getSuccess(T data) {
        Result<T> bean = new Result<>();
        bean.setCode(MCode.SUCCESS.getCode());
        bean.setMessage(MCode.SUCCESS.getMessage());
        bean.setData(data);
        return bean;
    }
    
    public static <T> Result<T> getError(T data, String message) {
        Result<T> bean = new Result<>();
        bean.setData(data);
        bean.setCode(MCode.EXCEPTION.getCode());
        bean.setMessage(message);
        return bean;
    }
    
    public void makeThrowable(Throwable cause, String defaultMessage) {
        setCode(MCode.EXCEPTION.getCode());
        String info;
        ExceptionConfig config = SpringContextUtil.getBean(ExceptionConfig.class);
        if (config != null && config.isExport()) {
            info = cause.getMessage();
            if (!StringUtils.hasLength(info)) {
                info = cause.getClass().getName() + "->" + defaultMessage;
            }
        } else {
            info = cause.getClass().getName() + "->" + defaultMessage;
        }
        message = info;
    }
}
