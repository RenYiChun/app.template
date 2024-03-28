package com.lrenyi.template.core.util;

import lombok.Getter;

@Getter
public enum MCode {
    SUCCESS(200, "success"),
    SHOW_EXCEPTION_MESSAGE(201, "非法访问"),
    PASSWORD_ERROR(202, "密码错误"),
    EXCEPTION(500, "内部服务异常"),
    NO_PERMISSIONS(401, "没有权限访问对应的资源");
    private final int code;
    private final String message;
    
    MCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
