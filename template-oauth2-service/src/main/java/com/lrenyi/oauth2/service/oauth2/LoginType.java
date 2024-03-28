package com.lrenyi.oauth2.service.oauth2;

import lombok.Getter;

@Getter
public enum LoginType {
    USER_NAME("userName"),
    JOB_NUMBER("jobNumber");
    
    public static final String LOGIN_TYPE = "lt";
    public static final String LOGIN_USER_NAME_TYPE_KEY = "LOGIN_TYPE_USERNAME";
    public static final String LOGIN_SSO_TYPE_TYPE_KEY = "type";
    public static final String NAME_FIELD_KEY = "userName";
    public static final String PASSWORD_FIELD_KEY = "coder";
    private final String type;
    
    LoginType(String type) {
        this.type = type;
    }
}
