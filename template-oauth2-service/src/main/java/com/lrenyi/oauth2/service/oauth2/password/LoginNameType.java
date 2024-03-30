package com.lrenyi.oauth2.service.oauth2.password;

import lombok.Getter;

@Getter
public enum LoginNameType {
    USER_NAME("username"),
    JOB_NUMBER("jobNumber");
    
    private final String code;
    
    LoginNameType(String code) {
        this.code = code;
    }
}
